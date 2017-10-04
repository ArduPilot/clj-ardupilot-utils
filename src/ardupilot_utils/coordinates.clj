(ns ardupilot-utils.coordinates)

(defonce ^:const EARTH_RADIUS 6371000.0)

(defn haversine
  "Computes the distance between two lat lon points"
  ^double [point1 point2]
  (let [lat1 (Math/toRadians ^double (:latitude point1))
        lon1 (Math/toRadians ^double (:longitude point1))
        lat2 (Math/toRadians ^double (:latitude point2))
        lon2 (Math/toRadians ^double (:longitude point2))
        a (+ (Math/pow (Math/sin (/ (- lat1 lat2) 2.0)) 2.0)
             (* (Math/cos lat1)
                (Math/cos lat2)
                (Math/pow (Math/sin (/ (- lon1 lon2) 2.0)) 2.0)))
        c (* 2.0 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1.0 a))))
        d (* c EARTH_RADIUS)]
    d))
