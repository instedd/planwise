(ns planwise.util.numbers)

(defn- scale [x y]
  (if (or (zero? x) (zero? y))
    1
    (Math/abs x)))

(defn float=
  ([x y] (float= x y 0.00001))
  ([x y epsilon] (<= (Math/abs (- x y))
                     (* (scale x y) epsilon))))

(defn nilable-float=
  "Like float= but tolerates nil parameters"
  ([x y] (nilable-float= x y 0.00001))
  ([x y epsilon]
   (cond
     (and (nil? x) (nil? y)) true
     (and x y) (float= x y)
     :else false)))

(defn float<
  ([x y] (float< x y 0.00001))
  ([x y epsilon] (< x
                    (- y (* (scale x y) epsilon)))))

(defn float>
  ([x y] (float< y x))
  ([x y epsilon] (float< y x epsilon)))

(defn float<=
  ([x y] (not (float> x y)))
  ([x y epsilon] (not (float> x y epsilon))))

(defn float>=
  ([x y] (not (float< x y)))
  ([x y epsilon] (not (float< x y epsilon))))
