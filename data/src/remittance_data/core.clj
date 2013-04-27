(ns remittance-data.core
  (:use [remittance-data.utils])
  (:use [incanter.core])
  (:use [incanter.io])
;  (:use [incanter.stats])
  (:use [incanter.excel])
  (:require [clojure.data.csv :as csv])
  (:require [clojure.java.io  :as io])
  (:require [clojure.data.json :as json])
;  (:require [clj-diff.core :as diff])
  )






; what we need
;
; 1. remittances unilateral with country codes, german names (and optionally english),
;    coordinates + 2012?
; 2. migration bilateral (top 75% flows) 1970-2000 + 2010
; 3. migration unilateral outgoing totals (but 100% of the flows!) for each country in each decade
; 4. OECD development aid unilateral incoming totals for each recipient country




(def remittances-dataset (read-xls "data-original/RemittancesData_Inflows_Nov12.xlsx"))

(def aid-dataset (read-dataset
   "data-original/oecd/REF_TOTAL_ODF_Data_a83fb694-6ff5-4883-91cf-f79a5e557c69.csv"
   :header true))





(def un-country-codes (read-dataset
   "data-original/un-country-codes.csv"
   :header true))

(let [iso2-by-iso3
  (group-by :iso3 (:rows (read-dataset "data-original/countries-iso2to3.csv" :header true)))]

(defn get-iso2 [iso3]
  (or
    (:iso2 (first (get iso2-by-iso3 iso3)))
    (do
      (println (str "WARNING! iso2 code for '" iso3 "' not found."))
      nil)
  )))



; to get the rows use   (:rows aid)


(let [
  get-info (fn [country iso2 lang]
  (let [
    url  (str
        "http://maps.googleapis.com/maps/api/geocode/json?"
        (encode-url-params {
          "sensor"     false
          "language"   lang
          "components" "types"
          "address"    (str country "," iso2)
        })
    )
    response (do
        (Thread/sleep 200)
        (json/read-str (fetch-url url))
    )
    results  (if (= (get response "status") "OK") (get response "results"))
    country-results    (filter #(in? (get % "types") "country") results)

   ]
    (if (empty? country-results)
      (do
;        (println (str
;          "WARNING! Could not obtain country info for '" country
;                   "' through Google Maps API. URL: " url))
        nil
      )

      country-results
    )


   ))
  ]

(def get-maps-api-country-info (memoize get-info)))




(defn my-distance [name1 name2]
  (let [
     common      (common-word-prefixes (.toLowerCase name1) (.toLowerCase name2) 2)
     common-len  (map count common)
    ]

    (reduce - 0 (map #(pow 2 %) common-len))
 ))






(defn find-closest-country-code [name]
  (let [
     closest (apply min-key
                #(my-distance name (:name %))
                (:rows un-country-codes)
               )

    ]
    (do
      (println (str "Using the closest country code: " closest))
      (newline)
      (:code closest))))




(let [un-contry-codes-by-name (group-by :name (:rows un-country-codes))]

(defn find-country-code [name]
  (or
    ; problematic countries
    (case name
      "Congo, Rep."             "COG"
      "Kosovo"                  "XXK"
      "Kyrgyz Republic"         "KGZ"
      "Slovak Republic"         "SVK"
      "United States"           "USA"
      ("West Bank and Gaza"
       "West Bank & Gaza Strip")  "PSE"


      ; countries found by distance search  (added to avoid warnings)
      "Bahamas, The"  "BHS"
      "Bolivia"   "BOL"
      "Congo, Dem. Rep."  "COD"
      "Egypt, Arab Rep."  "EGY"
      "Gambia, The"   "GMB"
      "Hong Kong SAR, China"  "HKG"
      "Iran, Islamic Rep."  "IRN"
      "Korea, Dem. Rep."  "PRK"
      "Korea, Rep."   "KOR"
      "Lao PDR"   "LAO"
      "Macao SAR, China"  "MAC"
      "Macedonia, FYR"  "MKD"
      "Micronesia, Fed. Sts."   "FSM"
      "Moldova"   "MDA"
      "São Tomé and Principe"   "STP"
      "St. Kitts and Nevis"   "KNA"
      "St. Lucia"   "LCA"
      "St. Martin (French part)"  "MAF"
      "St. Vincent and the Grenadines"  "VCT"
      "Tanzania"  "TZA"
      "United Kingdom"  "GBR"
      "Venezuela, RB"   "VEN"
      "Vietnam"   "VNM"
      "Virgin Islands (U.S.)"   "VIR"
      "Yemen, Rep."   "YEM"


      "Virgin Islands (UK)"  "VIR"
      "Cote d'Ivoire"  "CIV"
      "Bosnia-Herzegovina"   "BIH"
      "Syria"  "SYR"
      "Hong Kong, China"   "HKG"
      "Central African Rep."   "CAF"
      "Wallis & Futuna"  "WLF"
      "Korea"  "KOR"
      "Sao Tome & Principe"  "STP"
      "Brunei"   "BRN"
      "St.Vincent & Grenadines"  "VCT"
      "Northern Marianas"  "MNP"
      "Micronesia, Fed. States"  "FSM"
      "Iran"   "IRN"
      "Laos"   "LAO"
      "Venezuela"  "VEN"
      "St. Helena"   "SHN"
      "Macao"  "MAC"
      "St. Kitts-Nevis"  "KNA"



      ; countries to ignore (from OECD aid)
      (
        "Netherlands Antilles"
        "Mekong Delta Project"
        "States Ex-Yugoslavia"
        "Chinese Taipei"
        "Indus Basin"
        "East African Community"

      ) "-"

      "All recipients, Total" "-ALL-"

      nil
      )


    ; search in the UN countries list
    (:code (first (get un-contry-codes-by-name name)))

    (do  ; find the closest name
      (newline) (newline)
      (println (str "WARNING! No code found for '" name "'"))

      (find-closest-country-code name)
    ))))




(let [
   country-key       "Migrant remittance Inflows (US$ million)"
   value-columns     (filter #(or (number? %) (= % "2012e")) (:column-names remittances-dataset))
   has-value?        (fn [row col] (number? (get row col)))
   keep-row?         (fn [row]
                         ;(some #(has-value? row %) value-columns)  ;keep only non-empty value rows
                         (get row "2012e")  ;keep only rows where "2012e" has a value
                                             ; (which may be empty)
                     )

   prepare-remittances (fn [rows]
    (for [row rows :when (keep-row? row)]
       (let [
         name   (get row country-key)
         iso3   (find-country-code name)
         iso2   (get-iso2 iso3)
         info   (or
           (case iso3
             "LAO"  (get-maps-api-country-info "Laos" "" "de")
             nil)
           (get-maps-api-country-info name iso2 "de")
           (get-maps-api-country-info "" iso2 "de")
           (get-maps-api-country-info name "" "de")
           (println (str "WARNING! No info for country '" name "', '" iso2 "'"))
         )
       ]
       (merge
         {
          :name name
          :iso3 iso3
          :name_de (-> info first (get ,,, "address_components") first (get ,,, "long_name"))
          :lat     (-> info first (get ,,, "geometry") (get ,,, "location") (get ,,, "lat"))
          :lon     (-> info first (get ,,, "geometry") (get ,,, "location") (get ,,, "lng"))
          }
         (into {}  (for [year  value-columns :when (has-value? row year)]
            [
                (keyword (.substring (str year) 0 4))
                (round (get row year) :precision 3)
            ]
         ))
       ))
     )
  )]

(def remittances (prepare-remittances (:rows remittances-dataset))))





(def aid
  (let [rows (filter
              #(= "Current Prices (USD millions)" (get % (keyword "Amount type")))
              (:rows aid-dataset))

        rows (remove #(.contains (:Recipient %) ", regional") rows)

        by-recipient (group-by :Recipient rows)

        accepted-countries  (concat (map :iso3 remittances) ["-ALL-"])
     ]

    (into {}
      (remove nil?
        (for [[recipient, records] by-recipient]
          (let [iso3 (find-country-code recipient)]
            (if (in? accepted-countries iso3)
              [
                iso3

                (into {} (for [r records]
                  [ (keyword (str (get r :Year)))  (get r :Value) ]))
              ]
            )))))))



(save-to-json aid "../site/data/oecd-aid.json")

(defn transform []
  (save-to-csv remittances "../site/data/remittances.csv"))


(comment

  (filter #(not (second %)) (map (fn [r] [(:name r) (:name_de r)]) remittances))

  (prepare-remittances (take 2 (:rows remittances-dataset)))

  (take 2 remittances)

  (println (json/write-str (take 2 remittances)))



  (find-country-memo "Congo" "de")


  (count (filter #(= "Current Prices (USD millions)"
                    (get % (keyword "Amount type")))
           (:rows aid)))

)






