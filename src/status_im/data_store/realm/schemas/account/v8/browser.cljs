(ns status-im.data-store.realm.schemas.account.v8.browser)

(def schema {:name       :browser
             :primaryKey :browser-id
             :properties {:browser-id    :string
                          :name          :string
                          :timestamp     :int
                          :dapp?         {:type    :bool
                                          :default false}
                          :history-index {:type     :int
                                          :optional true}
                          :history       {:type     "string[]"
                                          :optional true}}})
