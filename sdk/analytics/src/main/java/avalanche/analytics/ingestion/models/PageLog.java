package avalanche.analytics.ingestion.models;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import avalanche.core.ingestion.models.LogWithProperties;
import avalanche.core.ingestion.models.utils.LogUtils;

import static avalanche.core.ingestion.models.CommonProperties.NAME;

/**
 * Page log.
 */
public class PageLog extends LogWithProperties {

    public static final String TYPE = "page";

    /**
     * Name of the page.
     */
    private String name;

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Get the name value.
     *
     * @return the name value
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the name value.
     *
     * @param name the name value to set
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void read(JSONObject object) throws JSONException {
        super.read(object);
        setName(object.getString(NAME));
    }

    @Override
    public void write(JSONStringer writer) throws JSONException {
        super.write(writer);
        writer.key(NAME).value(getName());
    }

    @Override
    public void validate() throws IllegalArgumentException {
        super.validate();
        LogUtils.checkNotNull(NAME, getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PageLog pageLog = (PageLog) o;

        return name != null ? name.equals(pageLog.name) : pageLog.name == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}