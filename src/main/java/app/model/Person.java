package app.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import framework.annotation.Id;
import framework.annotation.Letters;
import framework.annotation.Required;
import framework.annotation.Size;
import framework.annotation.Time;
import framework.annotation.Time.Unit;
import framework.annotation.Valid.Delete;
import framework.annotation.Valid.Save;
import framework.annotation.Valid.Update;

/**
 * data
 */
public class Person {
    /**
     * primary key
     */
    @Required({ Update.class, Delete.class })
    @Id
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
     * gender
     */
    public enum Gender {
        
        /**
         * unknown
         */
        UNKNOWN(0),
        
        /**
         * male
         */
        MALE(1),
        
        /**
         * female
         */
        FEMALE(2),
        ;
        
        /**
         * database value
         */
        int value;
        
        /**
         * @param value database value
         */
        private Gender(int value) {
            this.value = value;
        }
    }
    
    /**
     * gender
     */
    @Letters(Letters.KATAKANA)
    Gender gender;

    /**
     * birth day
     */
    @Time(past = 130, future = 0, unit = Unit.YEARS)
    Optional<LocalDate> birthday;
    
    /**
     * @return age
     */
    public Optional<Long> getAge() {
        return birthday.map(i -> ChronoUnit.YEARS.between(i, LocalDate.now()));
    }
    
    /**
     * @return id
     */
    public Integer getId() {
        return id;
    }
}
