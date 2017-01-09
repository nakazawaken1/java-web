package app.model;

import java.time.LocalDate;
import java.util.Optional;

import framework.Tool;
import framework.annotation.Letters;
import framework.annotation.Period;
import framework.annotation.Required;
import framework.annotation.Size;
import framework.annotation.Period.Unit;
import framework.annotation.Valid.Delete;
import framework.annotation.Valid.Save;
import framework.annotation.Valid.Update;

/**
 * data
 */
public class Data {
    /**
     * primary key
     */
    @Required({ Update.class, Delete.class })
    Integer id;

    /**
     * display name
     */
    @Required(Save.class)
    @Size(10)
    String name;

    /**
     * furigana
     */
    @Required(Save.class)
    @Size(20)
    @Letters(Letters.KATAKANA)
    String ruby;

    /**
     * birth day
     */
    @Period(past = 130, future = 0, unit = Unit.YEARS)
    Optional<LocalDate> birthday;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return Tool.dump(this);
    }
}
