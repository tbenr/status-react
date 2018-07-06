(ns status-im.ui.screens.browser.events
  (:require status-im.ui.screens.browser.navigation
            [status-im.utils.handlers :as handlers]
            [re-frame.core :as re-frame]
            [status-im.utils.random :as random]
            [status-im.i18n :as i18n]
            [status-im.ui.components.list-selection :as list-selection]
            [status-im.utils.universal-links.core :as utils.universal-links]
            [status-im.data-store.browser :as browser-store]
            [status-im.utils.http :as http]))

(re-frame/reg-fx
 :browse
 (fn [link]
   (if (utils.universal-links/universal-link? link)
     (utils.universal-links/open! link)
     (list-selection/browse link))))

(handlers/register-handler-fx
 :initialize-browsers
 [(re-frame/inject-cofx :data-store/all-browsers)]
 (fn [{:keys [db all-stored-browsers]} _]
   (let [browsers (into {} (map #(vector (:browser-id %) %) all-stored-browsers))]
     {:db (assoc db :browser/browsers browsers)})))

(handlers/register-handler-fx
 :browse-link-from-message
 (fn [_ [_ link]]
   {:browse link}))

(defn update-browser-fx [{:keys [db now]} browser]
  (let [updated-browser (assoc browser :timestamp now)]
    {:db            (update-in db [:browser/browsers (:browser-id updated-browser)]
                               merge updated-browser)
     :data-store/tx [(browser-store/save-browser-tx updated-browser)]}))

(defn update-browser-history-fx [cofx browser url loading]
  (cond
    loading
    nil

    (get-in cofx [:db :browser/options :dont-store-history-on-nav-change?])
    (assoc-in (update-browser-fx cofx browser)
              [:db :browser/options :dont-store-history-on-nav-change?]
              false)

    :else
    (let [history-index (:history-index browser)
          history       (:history browser)
          history-url   (try (nth history history-index) (catch js/Error _))
          new-history   (if (not= history-url url)
                          (conj (subvec history 0 (inc history-index))
                                url)
                          history)
          new-index     (dec (count new-history))]
      (update-browser-fx cofx
                         (assoc browser :history new-history :history-index new-index)))))

(defn update-browser-and-navigate [cofx browser]
  (merge (update-browser-fx cofx browser)
         {:dispatch [:navigate-to :browser (:browser-id browser)]}))

(handlers/register-handler-fx
 :open-dapp-in-browser
 [re-frame/trim-v]
 (fn [cofx [{:keys [name dapp-url]}]]
   (let [browser {:browser-id    name
                  :name          name
                  :dapp?         true
                  :history-index 0
                  :history       [(http/normalize-and-decode-url dapp-url)]}]
     (update-browser-and-navigate cofx browser))))

(handlers/register-handler-fx
 :open-url-in-browser
 [re-frame/trim-v]
 (fn [cofx [url]]
   (let [browser {:browser-id    (random/id)
                  :name          (i18n/label :t/browser)
                  :history-index 0
                  :history       [(http/normalize-and-decode-url url)]}]
     (update-browser-and-navigate cofx browser))))

(handlers/register-handler-fx
 :open-browser
 [re-frame/trim-v]
 (fn [cofx [browser]]
   (update-browser-and-navigate cofx browser)))

(handlers/register-handler-fx
 :update-browser
 [re-frame/trim-v]
 (fn [cofx [browser]]
   (update-browser-fx cofx browser)))

(handlers/register-handler-fx
 :update-browser-on-nav-change
 [re-frame/trim-v]
 (fn [cofx [browser url loading]]
   (update-browser-history-fx cofx browser url loading)))

(handlers/register-handler-fx
 :update-browser-options
 [re-frame/trim-v]
 (fn [{:keys [db]} [options]]
   {:db (update db :browser/options merge options)}))

(handlers/register-handler-fx
 :remove-browser
 [re-frame/trim-v]
 (fn [{:keys [db]} [browser-id]]
   {:db            (update-in db [:browser/browsers] dissoc browser-id)
    :data-store/tx [(browser-store/remove-browser-tx browser-id)]}))

(handlers/register-handler-fx
 :browser-nav-back
 [re-frame/trim-v]
 (fn [cofx [browser]]
   (let [back-index (:history-index browser)]
     (when (not (zero? back-index))
       (assoc-in (update-browser-fx cofx (assoc browser :history-index (dec back-index)))
                 [:db :browser/options :dont-store-history-on-nav-change?] true)))))

(handlers/register-handler-fx
 :browser-nav-forward
 [re-frame/trim-v]
 (fn [cofx [browser]]
   (let [forward-index (:history-index browser)]
     (when (< forward-index (dec (count (:history browser))))
       (assoc-in (update-browser-fx cofx (assoc browser :history-index (inc forward-index)))
                 [:db :browser/options :dont-store-history-on-nav-change?] true)))))