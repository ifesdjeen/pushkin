(dotimes [i 50]
  (propagate
   {:data { :hostname "sfplus-cl1-01"
           :type "memory_usage"
           :additional_info {"Verbose" false
                             "ObjectPendingFinalizationCount" 0
                             "HeapMemoryUsage" {"committed" 3215982592 "init" 3221225472 "max" 7426736128 "used" 2448939616}
                             "NonHeapMemoryUsage"{"committed" 368181248 "init" 270991360 "max" 369098752 "used" 337100328}}
           "tags" ["performance" "memory"]}}))
