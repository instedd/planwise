(ns planwise.util.numbers)

(defn float=

  ([x y]
   (float= x y 0.00001))

  ([x y epsilon]
   (cond

     (and (nil? x) (nil? y))
     true

     (and x y)
     (let [x (float x)
           y (float y)
           scale (if (or (zero? x) (zero? y)) 1 (Math/abs x))]
       (<= (Math/abs (- x y)) (* scale epsilon)))

     :else
     false)))
