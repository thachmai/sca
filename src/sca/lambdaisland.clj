(ns sca.lambdaisland
  (:require [reaver :refer [parse extract extract-from text attr]]
            [hiccup.core :refer [html]]
            [clj-http.client :as client])
  (:gen-class))

(def ^:private root "https://lambdaisland.com")
(def ^:private directory "output/")
(defn- url [& _] (apply str root _))
(def ^:private catalog (slurp (url "/episodes/all")))
(def ^:private parsed-catalog (parse catalog))

(defn- file-download [url path]
  (-> (client/get url {:as :stream :headers {"User-Agent" "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0"}})
      (:body)
      (clojure.java.io/copy (clojure.java.io/file path)))
  nil)
                                        ; (file-download "https://ia800209.us.archive.org/24/items/WildlifeSampleVideo/Wildlife.ogv" "videos/wild.ogv")

(defn- all-episodes []
  "return a collection of episodes"
  (extract-from parsed-catalog ".bb-butlast"
                [:title :uri :published :description]
                ".w-75-ns > span > a:first-of-type" text
                ".w-75-ns > span > a:first-of-type" (attr :href)
                ".published-at" text
                ".description" #(.html %)))

(defn- extract-mp4 [url referer]
  (Thread/sleep 500)
  (->> (client/get url {:headers {"Referer" referer}})
       (:body)
       (re-matches #"(?s).*\"profile\":174,\"width\":1280,\"mime\":\"video\/mp4\",\"fps\":25,\"url\":\"([^\"]*).*")
       (last)))
#_(extract-mp4 "https://player.vimeo.com/video/299286633?badge=0&autopause=0&player_id=0&app_id=70655" "https://lambdaisland.com/episodes/a-la-carte-polymorphism-2")

(defn- episode [url]
  (let [page (first (extract-from (parse (slurp url)) "#container"
                                  [:notes :vimeo-url]
                                  ".show-notes" #(.html %)
                                  "iframe" (attr :src)))
        mp4-url (extract-mp4 (:vimeo-url page) url)
        name (last (re-matches #".*/(.*)" url))]
    (assoc page :mp4-url mp4-url :mp4-name (str name ".mp4"))))
#_(episode (url "/episodes/a-la-carte-polymorphism-2"))

#_(generate-hiccup  (clojure.edn/read-string (slurp (str directory "lambdaisland.edn"))))
(defn- video-id [episode] (last (re-matches #"(.*)\..*" (:mp4-name episode))))
(defn- notes-id [episode] (str (video-id episode) "-notes"))
(defn- notes-toggle-js [episode]
  (str "const notes = document.querySelector('#" (notes-id episode) "');"
       "notes.style.display = notes.style.display === 'none' ? '' : 'none';"))
(defn- generate-hiccup [catalog]
  (let [css ".season { padding-top: 6em; } .button { background: #606c76; border: none; box-shadow: 0 0 2px grey;}"
        episode-mapper #(identity [:section.container
                                   [:div.row
                                    [:div.column.column-60 {:style "font-weight: bold;"} (:title %1)]
                                    [:div.column.column-20 (:published %1)]
                                    [:div.column.column-10
                                     [:a.button {:onclick (notes-toggle-js %1)}  "Notes"]]
                                    [:div.column.column-10 {:style "text-align: right;"}
                                     [:a.button.afterglow {:href (str "#" (video-id %1))} "View"]]]
                                   [:div.row
                                    [:video {:id (video-id %1) :src (:mp4-name %1) :width "1280" :height "720"}] ;afterglow needs width+height for lightbox
                                    [:div.column (:description %1)]]
                                   [:div.row
                                    [:div.column {:id (notes-id %1) :style "display: none"} (:notes %1)]]])
        ]
    (->>
     (html
      [:html
       [:meta {:charset "UTF-8"}]
       [:link {:rel "stylesheet" :href "//fonts.googleapis.com/css?family=Roboto:300,300italic,700,700italic"}]
       [:link {:rel "stylesheet" :href "//cdn.rawgit.com/necolas/normalize.css/master/normalize.css"}]
       [:link {:rel "stylesheet" :href "//cdn.rawgit.com/milligram/milligram/master/dist/milligram.min.css"}]
       [:style css]
       [:body {:style "height: 100%; padding: 100px;"} ; afterglow lightbox behaves weirdly without body height
        (map episode-mapper catalog)
        [:script {:src "//cdn.jsdelivr.net/npm/afterglowplayer@1.x"}]]])
     (spit (str directory "index.html")))))

(defn do-it
  "Execution entry point"
  [& args]
  (clojure.java.io/make-parents (str directory "videos/dummy"))
  (let [all (all-episodes)
        full (map #(merge % (episode (url (:uri %)))) all)]
    (clojure.pprint/pprint full (clojure.java.io/writer (str directory "lambdaisland.edn")))))

(defn download-mp4s [r]
  (doseq [e r]
    (let [f (:mp4-name e)]
      (try
        (println "TRYING" f)
        (file-download (:mp4-url e) (str directory f))
        (Thread/sleep 30000)
        (catch Exception exc
          (println "FAIL" f))))))

(defn go-go []
  (do-it)
  (download-mp4s  (clojure.edn/read-string (slurp (str directory "lambdaisland.edn")))))

#_(def scrap-result (clojure.edn/read-string (slurp (str directory "lambdaisland.edn"))))
#_(download-mp4s scrap-result)
