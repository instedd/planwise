(ns planwise.client.handlers
  (:require [planwise.client.db :as db]
            [planwise.client.api :as api]
            [planwise.client.routes :as routes]
            [planwise.client.projects.handlers :as projects]
            [planwise.client.current-project.handlers :as current-project]
            [planwise.client.datasets.handlers]
            [planwise.client.regions.handlers :as regions]
            [re-frame.utils :as c]
            [re-frame.core :refer [dispatch register-handler]]))

;; Event handlers
;; -----------------------------------------------------------------------

(register-handler
 :initialise-db
 (fn [_ _]
   (dispatch [:regions/load-regions])
   db/initial-db))

(defmulti on-navigate (fn [db page params] page))

(defmethod on-navigate :projects [db page {id :id, section :section, :as page-params}]
  (let [id (js/parseInt id)]
    (dispatch [:current-project/navigate-project id section])
    db))

(defmethod on-navigate :home [db _ _]
  db)

(defmethod on-navigate :datasets [db _ _]
  db)

(defmethod on-navigate :default [db _ _]
  db)

(register-handler
 :navigate
 (fn [db [_ {page :page, :as params}]]
   (let [new-db (assoc db
                 :current-page page
                 :page-params params)]
     (on-navigate new-db page params))))

(register-handler
 :signout
 (fn [db [_]]
   (api/signout :after-signout)
   db))

(register-handler
 :after-signout
 (fn [db [_ data]]
   (let [url (or (:redirect-to data) (routes/home))]
     (set! (.-location js/window) url))
   db))

(register-handler
 :message-posted
 (fn [db [_ message]]
   (cond
     (= message "authenticated")
     (dispatch [:datasets/load-resourcemap-info])

     (#{"react-devtools-content-script"
        "react-devtools-bridge"}
      (aget message "source"))
     nil   ; ignore React dev tools messages

     true
     (do
       (println message)
       (c/warn "Invalid message received " message)))
   db))

(register-handler
 :tick
 (fn [db [_ time]]
   (when (= 0 (mod time 1000))
     (dispatch [:datasets/refresh-datasets time]))
   db))
