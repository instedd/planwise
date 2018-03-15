(ns planwise.client.effects
  (:require [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx :as http-fx]
            [accountant.core :as accountant]))

(rf/reg-fx
 :navigate
 (fn [route]
   (accountant/navigate! route)))

(rf/reg-fx
 :delayed-dispatch
 (fn [{:keys [ms dispatch key] :or {:ms 0}}]
   (let [timeout (js/setTimeout #(rf/dispatch dispatch) ms)]
     (swap! rf-db/app-db update-in key (fn [prev-timeout]
                                         (js/clearTimeout prev-timeout)
                                         timeout)))))

(rf/reg-fx
 :cancel-dispatch
 (fn [key]
   (swap! rf-db/app-db update-in key js/clearTimeout)))

(rf/reg-fx
 :location
 (fn [url]
   (set! (.-location js/window) url)))

(def default-xhrio-options
	{:timeout         10000
	 :format          (ajax/json-request-format)
	 :response-format (ajax/json-response-format {:keywords? true})})

;; API effect - based on code from https://github.com/Day8/re-frame-http-fx
;; Original license notice follows:

;; The MIT License (MIT)

;; Copyright (c) 2016 Michael Thompson

;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:

;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.

;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.

(defn request->xhrio-options
  [{:as   request
    :keys [on-success on-failure mapper-fn]
    :or   {on-success      [:http-no-on-success]
           on-failure      [:http-no-on-failure]
           mapper-fn       identity}}]
                                        ; wrap events in cljs-ajax callback
  (let [api (new js/goog.net.XhrIo)]
    (merge default-xhrio-options
           (-> request
               (assoc
                :api     api
                :handler (partial http-fx/ajax-xhrio-handler
                                  #(->> % mapper-fn (conj on-success) rf/dispatch)
                                  #(rf/dispatch (conj on-failure %))
                                  api))
               (dissoc :on-success :on-failure :mapper-fn)))))

(defn request->options-callback
  [{:as   request
    :keys [on-success-cb on-failure-cb mapper-fn]
    :or   {on-success-cb (partial rf/console :log)
           on-failure-cb (partial rf/console :error)
           mapper-fn     identity}}]
                                        ; wrap events in cljs-ajax callback
  (let [api (new js/goog.net.XhrIo)]
    (merge default-xhrio-options
           (-> request
               (assoc
                :api     api
                :handler (partial http-fx/ajax-xhrio-handler
                                  (comp on-success-cb mapper-fn)
                                  on-failure-cb
                                  api))
               (dissoc :on-success-cb :on-failure-cb :mapper-fn)))))

(defn api-effect
  [request]
  (let [seq-request-maps (if (sequential? request) request [request])]
    (doseq [{:keys [key] :as request} seq-request-maps]
      (let [xhrio (-> request
                      (dissoc :key)
                      request->xhrio-options
                      ajax/ajax-request)]
        (when key (swap! rf-db/app-db assoc-in key xhrio))))))


(rf/reg-fx :api api-effect)

(rf/reg-fx
 :api-abort
 (fn [key]
   (some-> (get-in @rf-db/app-db key) ajax.protocols/-abort)
   (swap! rf-db/app-db assoc-in key nil)))

(defn make-api-request
  "Allows manually triggering an API request. Use :on-success-cb and
  :on-failure-cb to provide handler functions instead of the dispatch vectors.
  :key option is not supported."
  [request]
  (-> request request->options-callback ajax/ajax-request))

(def deferred-actions (atom {}))

(defn dispatch-debounce
  [dispatch-map-or-seq]
  (let [cancel-timeout (fn [id]
                          (when-let [deferred (get @deferred-actions id)]
                            (js/clearTimeout (:timer deferred))
                            (swap! deferred-actions dissoc id)))
        run-action (fn [action event]
                      (cond
                        (= :dispatch action) (rf/dispatch event)
                        (= :dispatch-n action) (doseq [e event]
                                                      (rf/dispatch e))))]
    (doseq [{:keys [id timeout action event]}
            (cond-> dispatch-map-or-seq
              (not (sequential? dispatch-map-or-seq)) vector)]
      (cond
        (#{:dispatch :dispatch-n} action)
        (do (cancel-timeout id)
            (swap! deferred-actions assoc id
                    {:timer (js/setTimeout (fn []
                                            (cancel-timeout id)
                                            (run-action action event))
                                          timeout)}))

        (= :cancel action)
        (cancel-timeout id)

        (= :flush action)
        (when-let [{:keys [id action event]} (get @deferred-actions id)]
          (cancel-timeout id)
          (run-action action event))

        :else
        (throw (js/Error (str ":dispatch-debounce invalid action " action)))))))
  
(rf/reg-fx 
  :dispatch-debounce 
  dispatch-debounce)