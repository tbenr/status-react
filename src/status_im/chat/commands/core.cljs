(ns status-im.chat.commands.core
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.components.colors :as colors]
            [status-im.i18n :as i18n]
            [status-im.chat.styles.message.message :as style]))

(defprotocol Command
  "Protocol for defining command message behaviour"
  (id [this] "Identifier of the command, used to look-up command display name as well")
  (scope [this]
    "Scope of the command, defined as set of values representing contexts
     where in which command is available, together with `id` it forms unique
     identifier for each command.
     Available values for the set are: 
     `id-of-the-any-chat` - command if available only for the specified chat
     `:personal-chats` - command is available for any personal 1-1 chat
     `:group-chats` - command is available for any group chat
     `:public-chats` - command is available for any public chat
     `:requested` - command is available only when there is an outstanding request")
  (parameters [this]
    "Ordered sequence of command parameter templates, where each parameter 
     is defined as map consisting of mandatory `:id`, `:title` and `:type` keys, 
     and optional `:suggestions` field.
     When used, `:suggestions` containes reference to any generic helper component
     rendering suggestions for the argument (input code will handle when and where
     to render it)")
  (validate [this parameters cofx]
    "Function validating the parameters once command is send. Takes parameters map
    and `cofx` map as argument, returns either `nil` meaning that no errors were
    found and command send workflow can proceed, or sequence of errors to display")
  (on-send [this message-id parameters cofx]
    "Function which can provide any extra effects to be produced in addition to 
    normal message effects which happen whenever message is sent")
  (short-preview [this command-message]
    "Function rendering the short-preview of the command message, used when
    displaying the last message in list of chats on home tab.
    There is no argument names `parameters` anymore, as the message object
    contains everything needed for short-preview/preview to render.")
  (preview [this command-message]
    "Function rendering preview of the command message in message stream"))

;; Example of command implementation

(defn send-short-preview
  [{:keys [content]}]
  (let [parameters (:params content)]
    [react/text {}
     (str (i18n/label :command-sending)
          (i18n/label-number (:amount parameters))
          " "
          (:asset parameters))]))

(defview send-status [tx-hash outgoing]
  (letsubs [confirmed? [:transaction-confirmed? tx-hash]
            tx-exists? [:wallet-transaction-exists? tx-hash]]
    [react/touchable-highlight {:on-press #(when tx-exists?
                                             (re-frame/dispatch [:show-transaction-details tx-hash]))}
     [react/view style/command-send-status-container
      [vector-icons/icon (if confirmed? :icons/check :icons/dots)
       {:color           colors/blue
        :container-style (style/command-send-status-icon outgoing)}]
      [react/view
       [react/text {:style style/command-send-status-text}
        (i18n/label (cond
                      confirmed? :status-confirmed
                      tx-exists? :status-pending
                      :else :status-tx-not-found))]]]]))

(defview send-preview
  [{:keys [content timestamp-str outgoing group-chat]}]
  (letsubs [network [:network-name]]
    (let [{{:keys [amount fiat-amount tx-hash asset] send-network :network} :params} content
          recipient-name (get-in content [:params :bot-db :public :recipient])
          network-mismatch? (and (seq send-network) (not= network send-network))]
      [react/view style/command-send-message-view
       [react/view
        [react/view style/command-send-amount-row
         [react/view style/command-send-amount
          [react/text {:style style/command-send-amount-text
                       :font  :medium}
           amount
           [react/text {:style (style/command-amount-currency-separator outgoing)}
            "."]
           [react/text {:style (style/command-send-currency-text outgoing)
                        :font  :default}
            asset]]]]
        (when fiat-amount
          [react/view style/command-send-fiat-amount
           [react/text {:style style/command-send-fiat-amount-text}
            (str "~ " fiat-amount " " (i18n/label :usd-currency))]])
        (when (and group-chat
                   recipient-name)
          [react/text {:style style/command-send-recipient-text}
           (str
            (i18n/label :send-sending-to)
            " "
            recipient-name)])
        [react/view
         [react/text {:style (style/command-send-timestamp outgoing)}
          (str (i18n/label :sent-at) " " timestamp-str)]]
        [send-status tx-hash outgoing]
        (when network-mismatch?
          [react/text send-network])]])))

(deftype PersonalSendCommand []
  Command
  (id [_]
    :send)
  (scope [_]
    #{:personal-chats})
  (parameters [_]
    [{:id :asset
      :type :text
      ;; Suggestion components should be structured in such way that they will just take
      ;; one argument, event-creator fn used to construct event to fire whenever something
      ;; is selected.
      ;; `:set-command-argument` will be event event defined in the chat input namespace,
      ;; taking parameter id and selected value as argument and correctly setting them in
      ;; the `[:chats chat-id :staged-command :parameters parameter-id]` db path.
      ;;
      ;; Example:
      #_:suggestions #_(suggestions/choose-asset #(vector :set-command-argument :asset %))}
     {:id :amount
      :type :number}])
  (validate [_ _ _]
    ;; There is no validation for the `/send` command, as it's fully delegated to the wallet
    nil)
  (on-send [_ message-id parameters cofx]
    ;; TODO janherich: express effect prefilling wallet `:wallet-send` state and navigating
    ;; to wallet
    (when-let [tx-hash (get-in cofx [:db :wallet :send-transaction :tx-hash])]
      {:dispatch [:update-transactions]}))
  (short-preview [_ command-message]
    (send-short-preview command-message))
  (preview [_ command-message]
    (send-preview command-message)))
