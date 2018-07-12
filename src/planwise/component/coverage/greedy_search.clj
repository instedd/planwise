(ns planwise.component.coverage.greedy-search
  (:require [planwise.engine.raster :as raster]
            [clojure.core.memoize :as memoize]
            [planwise.component.coverage :as coverage])
  (:import [java.lang.Math]
           [org.gdal.gdalconst gdalconst]))

;; Idea of algorithm:
 ;; i.   From random sampling of last quartile of demand estimate coverage and maximus distance covered from selected location.
 ;; ii.  Sort demand points by weight to set priorities.
 ;; iii. Given a high demand point, associate other demand points groupped by nearness considering bound in (i)
 ;; iv.  Once grouped, calculate centroide of group and apply coverage function.
 ;; ivi. Eval fitness of solution, repeat procedure."

;Auxiliar functions
(defn euclidean-distance [a b]
  (Math/pow  (reduce + (map #(-> (- %1 %2) (Math/pow 2)) a b))
             (/ 1 2)))

(defn pixel->coord
  [geotransform pixels-vec]
  (let [[x0 xres _ y0 _ yres] (vec geotransform)
        coord-fn (fn [[x y]] [(+ x0 (* x xres)) (+ y0 (* y yres))])]
    (coord-fn pixels-vec)))

(defn coord->pixel
  [geotransform coord-vec]
  (let [[x0 xres _ y0 _ yres] (vec geotransform)
        pix-fn (fn [[lon lat]] [(Math/round (/ (- lon x0) xres)) (Math/round (/ (- lat y0) yres))])]
    (pix-fn coord-vec)))


(defn get-pixel
  [idx xsize]
  (let [height (quot idx xsize)
        width (mod idx xsize)]
    [width height]))

(defn get-geo
  [idx {:keys [xsize ysize geotransform]}]
  (pixel->coord geotransform (get-pixel idx xsize)))

(defn get-index
  [[lon lat] {:keys [xsize geotransform]}]
  (let [[x y :as pixel-vec] (coord->pixel geotransform [lon lat])]
    (+ (* y xsize) x)))

(defn format*
  [[idx w :as val] raster]
  (let [[lon lat] (get-geo idx raster)]
    [lon lat w]))

(defn get-centroid
  [set-points]
  (let [[r0 r1 total-w]  (reduce (fn [[r0 r1 partial-w] [l0 l1 w]]
                                   [(+ r0 (* w l0)) (+ r1 (* w l1)) (+ partial-w w)]) [0 0 0] set-points)]
    (if (pos? total-w)
      (map #(/ % total-w) [r0 r1])
      (first set-points))))

(defn get-geo-centroid
  [set-points]
  (let [total (count set-points)
        [r0 r1] (reduce (fn [[r0 r1] [l0 l1]] [(+ r0 l0) (+ r1 l1)]) [0 0] set-points)]
    (if (pos? total) (map #(/ % total) [r0 r1]) (first set-points))))

;Initializing search
(defn get-demand
  [data [_ b0 b1 b2 _ :as demand-quartiles]]
  (let [indexed-data (map-indexed vector (vec data))
        initial-set  (sort-by last > (filter (fn [[idx val]] (> val b2)) indexed-data))]
    initial-set))

;TODO n to guarantee reliable result
;Or take coverage-criteria into consideration
(defn mean-initial-data
  [n demand coverage-fn]
  (let [locations (take n (random-sample 0.8 demand))
        [total-cov total-max] (reduce (fn [[tc tm] location]
                                        (let [{:keys [cov max]} (or (coverage-fn location) {:cov 0 :max 0})]
                                          [(+ tc cov) (+ tm max)])) [0 0] locations)]
    {:avg-cov (float (/ total-cov n))
     :avg-max (/ total-max n)}))

(defn neighbour-fn
  [[idx _] raster bound]
  (let [coord (get-geo idx raster)]
    (fn [[other _]] (< (euclidean-distance coord (get-geo other raster)) bound))))

(defn frontier-fn
  [[idx _] raster radius eps]
  (let [coord (get-geo idx raster)]
    (fn [[other _]] (< (- (euclidean-distance coord (get-geo other raster)) radius) eps))))

(defn next-neigh
  ([raster demand center radius]
   (next-neigh raster demand center radius 0))
  ([raster demand center radius eps]
   (let [in-frontier? (fn [p] (frontier-fn p raster radius eps))
         frontier (get (group-by (in-frontier? center) demand) true)]
     (get-centroid (map #(format* % raster) frontier)))))

(defn get-neighbour
  [demand-point {:keys [data] :as raster} demand {:keys [avg-max] :as bounds}]

  (let [is-neighbour? (fn [p] (neighbour-fn p raster avg-max))]

    (when (some? (get (group-by (is-neighbour? demand-point) demand) true))

      (loop [sum 0
             radius avg-max
             [idx _ :as center] demand-point]
        (if (<= (- avg-max sum) 0)

          (let [sep (group-by (is-neighbour? center) demand)]
            (println "success!")
            [(get sep true) (get sep false)])

          (let [location    (next-neigh raster demand center radius)]
            (if (nil? location)
              (recur avg-max radius center)
              (let [index       (get-index location raster)
                    next-center [index (aget data index)]
                    next-radius (euclidean-distance (get-geo idx raster) location)
                    step        (- radius next-radius)]

                (if (and (> step 0) (pos? next-radius))
                  (recur (+ sum step) next-radius next-center)
                  (recur avg-max radius center))))))))))

(defn get-groups
  [initial-set raster bounds]
  (loop [groups []
         from   (first initial-set)
         demand initial-set]

    (println "groups:" (count groups))

    (if (nil? from)

      groups

      (let [[group demand*] (get-neighbour from raster demand bounds)]
        (if (nil? group)
          (recur groups (take 1 demand) (drop 1 demand))
          (let [groups*         (into groups [group])
                from*           (first (sort-by second > demand*))]
            (recur groups* from* demand*)))))))

(defn greedy-search
  [coverage {:keys [data] :as raster} coverage-fn demand-quartiles n-sample]
  (let [initial-set   (get-demand data demand-quartiles)
        bounds        (mean-initial-data n-sample initial-set coverage-fn)
        groups        (map (fn [group] (map #(get-geo (first %) raster) group)) (get-groups initial-set raster bounds))
        geo-cent      (map get-geo-centroid groups)]
    (sort-by :cov > (map #(coverage-fn % true) geo-cent))))


(defn catch-exc
  [function & params]
  (try
    (apply function params)
    (catch Exception e
      take 10 (:trace e))))

;Greedy Search
(comment
  (require '[planwise.engine.raster :as raster])
  (require '[planwise.component.engine :as engine])
  (require '[clojure.core.memoize :as memoize])
  (def engine (:planwise.component/engine system))

;gdalwarp -tr 0.0016666667 -0.0016666667 initial-8026027092040813847.tif half-res-initial-8026027092040813847.tif
  (def raster (raster/read-raster "data/scenarios/44/initial-8026027092040813847.tif"))
  (def half-raster (raster/read-raster "data/scenarios/44/half-res-initial-8026027092040813847.tif"))

  (def demand-quartiles  [0.000015325084 0.00689443 0.09416369 0.8036073 230.33006])

  (def demand (planwise.component.coverage.greedy-search/get-demand (:data raster) demand-quartiles))
  (def demand-hr (planwise.component.coverage.greedy-search/get-demand (:data half-raster) demand-quartiles))

  (def criteria {:algorithm :simple-buffer :distance 20})
  (def criteria1 {:algorithm :pgrouting-alpha :driving-time 60})
  (def criteria {:algorithm :walking-friction :walking-time 120})

  (def coverage-fn
    (memoize/lu (fn ([val] (engine/coverage-fn (:coverage engine) val raster criteria)))))

  (def cost-fn (fn ([[idx _]] (catch-exc coverage-fn {:idx idx :res (float (/ 1 1200))}))
                 ([coord condition] (catch-exc coverage-fn {:coord coord :res (float (/ 1 1200))}))))

  ;changing resolution
  (def cov-fn
    (memoize/lu (fn ([val] (engine/coverage-fn (:coverage engine) val half-raster criteria)))))

  (def cost-fn* (fn ([[idx _]]
                     (catch-exc cov-fn {:idx idx :res (float (/ 1 600))}))
                  ([coord condition]
                   (catch-exc cov-fn {:coord coord :res (float (/ 1 600))}))))

  (def bounds (mean-initial-data 100 demand-hr cost-fn*))
  (planwise.component.coverage.greedy-search/get-fitted-location 100 half-raster demand-quartiles cost-fn* 10)
  (planwise.component.coverage.greedy-search/get-fitted-location 100 raster demand-quartiles cost-fn 10)

  (def bounds (planwise.component.coverage.greedy-search/mean-initial-data 100 demand-hr cost-fn*)))

;;Criteria
  ;Walking by distance

;With raster pixel resolution reduced to half
; ~different raster file~
  ; Brute-force
    ;{:cov 77374, :loc [40.1017698203 -3.2040743179000004]}
    ;{:cov 77298, :loc [40.1017698203 -3.2057409846000002]}
    ;{:cov 77183, :loc [40.1017698203 -3.2074076513000005]}
    ;{:cov 77180, :loc [40.100103153599996 -3.2090743180000003]}

;;Same algorithm

;(get-fitted-location 100 half-raster demand-quartiles cost-fn* 5)

  ;{:cov 115692, :loc (39.63792165663872 -3.8166030599945175)}
  ;{:cov 115692, :loc (39.63792165663872 -3.8166030599945175)}
  ;{:cov 115692, :loc (39.63792165663872 -3.8166030599945175)}
  ;{cov  115692, :loc (39.63792165663872 -3.8166030599945175)}
  ;{:cov 93150, ::loc (39.713670797350694 -3.7428425405810732)}

;(get-fitted-location 100 raster demand-quartiles cost-fn 5)

  ;{:cov 463048, :loc (39.638539971392 -3.817919752878605)}
  ;{:cov 463048, :loc (39.638539971392 -3.817919752878605)}
  ;{:cov 463048, :loc (39.638539971392 -3.817919752878605)}
  ;{:cov 463048, :loc (39.638539971392 -3.817919752878605)}
  ;{:cov 463048, :loc (39.638539971392 -3.817919752878605)}

;;Criteria
  ;Friction distance
  ;brute force: time: eternity

  ;First searching algorithm
;(get-fitted-location 100 half-raster demand-quartiles cost-fn* 5)

 ;{:cov 55946,  :loc (40.02966687117371 -3.2203575806172533)}
 ;{:cov 55946,  :loc (40.02966687117371 -3.2203575806172533)}
 ;{:cov 51841, :loc (40.08171796246033 -3.273976397705447)}
 ;{:cov 51841, :loc (40.08171796246033 -3.273976397705447)}
 ;{:cov 50765, :loc (40.107270732172246 -3.219772131366216)}

;(get-fitted-location 100 raster demand-quartiles cost-fn 5)

;{:cov 223860, :loc (40.02973668284678 -3.2206390122503374)}
;{:cov 223860, :loc (40.02973668284678 -3.2206390122503374)}
;{:cov 207453, :loc (40.08249788005785 -3.2746732245363437)}
;{:cov 207453, :loc (40.08249788005785 -3.2746732245363437)}
; {:cov 203802,:loc (40.10800196085446 -3.220519507464421)}

  ;Updated version of algorithm
  ;Grouping according to frontier centroids

  ;(greedy-search (:coverage engine) raster cost-fn demand-quartiles 100)

  ;Raster half-resolution
  ;{:cov 54396 :loc (40.07255495730021 -3.2077862144975446)}
  ;{:cov 36170 :loc (39.65313099476781 -3.7583439240644956)}
  ;{:cov 35174 :loc (39.57323424049557 -3.8826480031207766)}
  ;{:cov 32926 :loc (39.730818179349754 -3.880963937297571)}
  ;{:cov 31615 :loc (39.82450344590833 -3.6184687578471064)}
  ;{:cov 23993, :loc (39.787869566981875 -3.7603491394863418)}{:cov 18941, :loc (39.977893555196005 -3.3403248256755056)}{:cov 17595, :loc (39.46819296010421 -3.7823663432841665)} {:cov 16499, :loc (39.99367548328748 -3.172335571691125)} {:cov 15927, :loc (40.11641875549743 -3.037653690984775)}{:cov 11731, :loc (39.87434878793408 -3.469421800811859)} {;:cov 10891, :max 0.10410888498789064, :loc (39.51261891089442 -3.6817348482360024)} {:cov 9235, :max 0.10035634549805705, :loc (39.727687827262535 -3.4986115021042483)} {:cov 8984, :max 0.10522800216989486, :loc (39.54571611422642 -3.5192760227178375)} {:cov 8873, :max 0.0968962809629167, :loc (39.9001031496 -3.3574076543)} {:cov 8822, :max 0.09712760900941075, :loc (39.76155240633123 -3.3354721464563)} {:cov 8632, :max 0.091164739092297, :loc (39.70011335176484 -3.622290288705639)} {:cov 8356, :max 0.10192577805412577, :loc (39.864306115316545 -3.1637003904726857)} {:cov 8038, :max 0.10045912405425075, :loc (39.63814145853699 -3.536051336033795)} {:cov 7046, :max 0.10164265214962412, :loc (40.12122697057221 -2.879529248086835)} {:cov 5758, :max0.10068141794583295, :loc (39.98206042138932 -3.030490879839434)} {:cov 5676, :max 0.10291118612271435, :loc (39.5532281426625 -3.4246993223125006)} {:cov 5553, :max 0.10344220861657667, :loc (39.41757668492303 -3.6828177137182525)} {:cov 4499, :max 0.10661418554580661, :loc (39.74946425769831 -3.150240983490001)} {:cov 4052, :max 0.10521142945147134, :loc (39.6667698116 -3.3724076546000004)} {:cov 3796, :max 0.10135199610091923, :loc (39.993616764420956 -2.8787873356768237)} {:cov 3115, :max 0.10040885214918742, :loc (39.82023620122395 -3.0281149306273116)} {:cov 2081, :max 0.10433333982067255, :loc (40.22934558042727 -2.9858924953545465)} {:cov 2014, :max 0.10211718329640271, :loc (39.564244557024246 -3.2887965418166663)} {:cov 1711, :max 0.1039168379008399, :loc (40.144269821150004 -2.7437618086937503)}

  ;Raster original resolution
  ;{:cov 218113, :loc (40.07412985666196 -3.2082737134394685)}
  ;{:cov 144028, :loc (39.65885497818281 -3.7578519720469243)}
  ;{:cov 141284, :loc (39.573750772133025 -3.882683461174405)}
  ;{:cov 133032, :loc (39.73088725097774 -3.882673461890217)}
  ;{:cov 126987, :loc (39.82561602687024 -3.618993306649357)}
  ;{:cov 91605, :loc (39.7921986614436 -3.7628018105234093)}
  ; {:cov 75807, :max 0.10866052581761718, :loc (39.979337402169506 -3.340184753386399)} {:cov 70151, :max 0.10607382888359838, :loc (39.46947835657303 -3.782446747665354)} {:cov 65348, :max 0.10260124394011062, :loc (39.99534391084204 -3.173121647366522)} {:cov 63681, :max 0.10797015482714306, :loc (40.11698395830593 -3.038420264896815)} {:cov 46603, :max 0.09297532514533595, :loc (39.87498869919873 -3.4694585137867984)} {:cov 43058, :max 0.10280376962021394, :loc (39.51561661256287 -3.6831819518694444)} {:cov 40723, :max 0.10404542389541259, :loc (39.702745027729456 -3.6203982999682016)} {:cov 36876, :max 0.10187414578933869, :loc (39.72825777222708 -3.4995771536613316)} {:cov 35918, :max 0.10710129879768852, :loc (39.54644314588067 -3.520600739445839)} {:cov 35400, :max 0.09457324038700139, :loc (39.9018413625 -3.3574698625)} {:cov 35203, :max 0.09843186695739498, :loc (39.76222271171303 -3.336251640829089)} {:cov 33576, :max 0.10181675755444806, :loc (39.865158932749196 -3.164422205414533)} {:cov 32853, :max 0.10436544150305328, :loc (39.63856707742146 -3.539733996175207)} {:cov 28093, :max 0.10109933300184518, :loc (40.12189283502194 -2.8804273288954274)} {:cov 23906, :max 0.10427883824431766, :loc (39.42106304838951 -3.6815845814417165)} {:cov 22929, :max 0.10118586666879661, :loc (39.98184873988404 -3.0303186149486727)} {:cov 21927, :max 0.10701862026767217, :loc (39.59785602239998 -3.4350959240000023)} {:cov 18263, :max 0.09904895097459997, :loc (39.66996075714285 -3.3712937142857142)} {:cov 17800, :max 0.10803087819165016, :loc (39.74997506344541 -3.151586116386554)} {:cov 15294, :max 0.10222116031343072, :loc (39.994118941748724 -2.879121312594887)} {:cov 12363, :max 0.09978704897913593, :loc (39.82120946757933 -3.028532906441229)} {:cov 8378, :max 0.10603468105598113, :loc (40.229698991397846 -2.9868608075268823)} {:cov 8001, :max 0.10185576762205904, :loc (39.56492999772155 -3.289708068860762)} {:cov 6672, :max 0.10280862764443302, :loc (40.14509056071428 -2.7447413964285716)}