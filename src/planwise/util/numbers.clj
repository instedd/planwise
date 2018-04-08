(ns planwise.util.numbers)

;; abs version which works for any numeric type
;; Math/abs only works for Java's int, float and double
(defn abs
  "(abs n) is the absolute value of n"
  [n]
  (cond
    (not (number? n)) (throw (IllegalArgumentException.
                              "abs requires a number"))
    (neg? n) (- n)
    :else n))

(defn- scale [x y]
  (if (or (zero? x) (zero? y))
    1
    (abs x)))

(defn float=
  ([x y] (float= x y 0.00001))
  ([x y epsilon] (<= (abs (- x y))
                     (* (scale x y) epsilon))))

(defn nilable-float=
  "Like float= but tolerates nil parameters"
  ([x y] (nilable-float= x y 0.00001))
  ([x y epsilon]
   (cond
     (and (nil? x) (nil? y)) true
     (and x y) (float= x y epsilon)
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
