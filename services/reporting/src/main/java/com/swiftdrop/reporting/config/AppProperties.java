package com.swiftdrop.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed configuration bound from the {@code reporting.*} namespace in
 * application.yaml.  All values fall back to safe defaults so the app
 * starts in a local dev environment without any env vars set.
 */
@ConfigurationProperties(prefix = "reporting")
public class AppProperties {

    /** IANA timezone used to bucket events into calendar days. */
    private String timezone = "Asia/Colombo";

    private Queues    queues    = new Queues();
    private Exchanges exchanges = new Exchanges();
    private List<String> bindings = List.of("parcel.#", "courier.availability.changed");

    // ── Getters / setters ────────────────────────────────────────────────

    public String    getTimezone()  { return timezone;  }
    public void      setTimezone(String timezone) { this.timezone = timezone; }

    public Queues    getQueues()    { return queues;    }
    public void      setQueues(Queues queues) { this.queues = queues; }

    public Exchanges getExchanges() { return exchanges; }
    public void      setExchanges(Exchanges exchanges) { this.exchanges = exchanges; }

    public List<String> getBindings() { return bindings; }
    public void         setBindings(List<String> bindings) { this.bindings = bindings; }

    // ── Nested config records ────────────────────────────────────────────

    public static class Queues {
        private String main = "reporting.q";
        private String dlq  = "reporting.dlq";

        public String getMain() { return main; }
        public void   setMain(String main) { this.main = main; }
        public String getDlq()  { return dlq;  }
        public void   setDlq(String dlq)   { this.dlq  = dlq;  }
    }

    public static class Exchanges {
        private String events = "swiftdrop.events";
        private String dlx    = "swiftdrop.dlx";

        public String getEvents() { return events; }
        public void   setEvents(String events) { this.events = events; }
        public String getDlx()    { return dlx;    }
        public void   setDlx(String dlx)       { this.dlx    = dlx;    }
    }
}
