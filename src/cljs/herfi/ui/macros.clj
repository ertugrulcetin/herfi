(ns herfi.ui.macros
  (:require
    [applied-science.js-interop :as j]))

(defmacro create-comp [type opts]
  `(reagent.core/adapt-react-class
     (herfi.ui.components/styled* ~(name type) (j/lit ~opts))))
