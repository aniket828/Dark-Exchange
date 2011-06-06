(ns darkexchange.model.trade
  (:require [clj-record.boot :as clj-record-boot]
            [clojure.contrib.logging :as logging]
            [darkexchange.model.offer :as offer]
            [darkexchange.model.terms :as terms]
            [darkexchange.model.user :as user])
  (:use darkexchange.model.base)
  (:import [java.util Date]))

(def needs-to-be-confirmed-key :needs-to-be-confirmed)
(def waiting-to-be-confirmed-key :waiting-to-be-confirmed)
(def waiting-for-wants-key :waiting-for-wants)
(def send-wants-receipt-key :send-wants-receipt)
(def send-has-key :send-has)
(def waiting-for-has-receipt-key :waiting-for-has-receipt)

(def trade-add-listeners (atom []))
(def update-trade-listeners (atom []))

(defn add-trade-add-listener [listener]
  (swap! trade-add-listeners conj listener))

(defn add-update-trade-listener [listener]
  (swap! update-trade-listeners conj listener))

(defn trade-add [new-trade]
  (doseq [listener @trade-add-listeners]
    (listener new-trade)))

(defn trade-updated [trade]
  (doseq [listener @update-trade-listeners]
    (listener trade)))

(clj-record.core/init-model
  (:associations (belongs-to offer))
  (:callbacks (:after-insert trade-add)
              (:after-update trade-updated)))

(defn create-new-trade [trade-data]
  (insert
    (merge { :created_at (new Date) :user_id (:id (user/current-user)) }
      (select-keys trade-data [:offer_id :foreign_trade_id :wants_first]))))

(defn open-trades
  ([] (open-trades (user/current-user)))
  ([user] (find-records ["(closed IS NULL OR closed = 0) AND user_id = ?" (:id user)])))

(defn open-trade? [trade]
  (not (as-boolean (:closed trade))))

(defn needs-to-be-confirmed? [trade]
  (and (not (as-boolean (:accept_confirm trade))) (nil? (:foreign_trade_id trade))))

(defn waiting-to-be-confirmed? [trade]
  (and (not (as-boolean (:accept_confirm trade))) (:foreign_trade_id trade)))

(defn wants-sent? [trade]
  (as-boolean (:wants_sent trade)))

(defn wants-received? [trade]
  (as-boolean (:wants_received trade)))

(defn has-sent? [trade]
  (as-boolean (:has_sent trade)))

(defn has-received? [trade]
  (as-boolean (:has_received trade)))

(defn needs-to-be-confirmed-next-step-key [trade]
  (when (needs-to-be-confirmed? trade)
    needs-to-be-confirmed-key))

(defn waiting-to-be-confirmed-next-step-key [trade]
  (when (waiting-to-be-confirmed? trade)
    waiting-to-be-confirmed-key))

(defn confirmation-next-step-key [trade]
  (or (needs-to-be-confirmed-next-step-key trade) (waiting-to-be-confirmed-next-step-key trade)))

(defn wants-sent-next-step-key [trade]
  (when (wants-sent? trade)
    waiting-for-wants-key))

(defn wants-received-next-step-key [trade]
  (when (wants-received? trade)
    send-wants-receipt-key))

(defn wants-next-step-key [trade]
  (or (wants-sent-next-step-key trade) (wants-received-next-step-key trade)))

(defn has-sent-next-step-key [trade]
  (when (has-sent? trade)
    send-has-key))

(defn has-received-next-step-key [trade]
  (when (has-received? trade)
    waiting-for-has-receipt-key))

(defn has-next-step-key [trade]
  (or (has-sent-next-step-key trade) (has-received-next-step-key trade)))

(defn has-want-next-step-key [trade]
  (if (as-boolean (:wants_first trade))
    (or (wants-next-step-key trade) (has-next-step-key trade))
    (or (has-next-step-key trade) (wants-next-step-key trade))))

(defn next-step-key [trade]
  (or (confirmation-next-step-key trade) (has-want-next-step-key trade)))

(defn waiting-for-key-to-text [waiting-for-key]
  (cond
    (= waiting-for-key needs-to-be-confirmed-key) (terms/needs-to-be-confirmed)
    (= waiting-for-key waiting-to-be-confirmed-key) (terms/waiting-to-be-confirmed)
    (= waiting-for-key waiting-for-wants-key) (terms/waiting-for-payment-to-be-sent)
    (= waiting-for-key send-wants-receipt-key) (terms/confirm-payment-received)
    (= waiting-for-key send-has-key) (terms/send-payment)
    (= waiting-for-key waiting-for-has-receipt-key) (terms/waiting-for-payment-to-be-confirmed)))

(defn next-step-text [trade]
  (waiting-for-key-to-text (next-step-key trade)))

(defn convert-to-table-trade [trade]
  (let [offer (find-offer trade)]
    { :id (:id trade)
      :im-sending-amount (offer/has-amount-str offer)
      :im-sending-by (offer/has-payment-type-str offer)
      :im-receiving-amount (offer/wants-amount-str offer)
      :im-receiving-by (offer/wants-payment-type-str offer)
      :waiting-for (next-step-text trade) }))

(defn table-open-trades []
  (map convert-to-table-trade (open-trades)))