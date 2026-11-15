package com.kitchensink.service.port;


/** Everything a metrics backend must provide; consumers depend on the narrower ports instead. */
public interface MemberMetrics extends MemberActivityMetrics, ImportMetrics {}
