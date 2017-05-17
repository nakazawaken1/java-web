package app.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

import framework.AbstractBuilder;
import framework.annotation.Id;
import framework.annotation.InitialData;
import framework.annotation.Join;
import framework.annotation.Letters;
import framework.annotation.Mapping;
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
@Mapping("t_person")
@InitialData(field = "id, name, ruby, gender, birthday", value = { "1, '利用者　太郎', 'リヨウシャ　タロウ', 1, null", "2, '利用者　ハナコ', 'リヨウシャ　ハナコ', 2, DATE '2000-01-02'" })
public class Person {
    /**
     * primary key
     */
    @Required({ Update.class, Delete.class })
    @Id
    public final Integer id;

    /**
     * display name
     */
    @Required(Save.class)
    @Size(10)
    public final String name;

    /**
     * furigana
     */
    @Required(Save.class)
    @Size(20)
    @Letters(Letters.KATAKANA)
    public final String ruby;

    /**
     * gender
     */
    public enum Gender implements IntSupplier {

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
        FEMALE(2);

        /**
         * id
         */
        private int id;

        /**
         * @param id id
         */
        private Gender(int id) {
            this.id = id;
        }

        /**
         * @return id
         */
        @Override
        public int getAsInt() {
            return id;
        }
    }

    /**
     * gender
     */
    public final Gender gender;

    /**
     * birth day
     */
    @Time(past = 130, future = 0, unit = Unit.YEARS)
    public final Optional<LocalDate> birthday;

    /**
     * @return age
     */
    public Optional<Long> getAge() {
        return birthday.map(i -> ChronoUnit.YEARS.between(i, LocalDate.now()));
    }

    /**
     * license
     */
    @Join(table = "t_person_license", from = "id:person_id",
        to = "id:license_id"/* "SELECT * FROM license l INNER JOIN pserson_license pl ON pl.person_id = ${id} AND l.id = pl.license_id" */)
    public List<License> licenses;

    /**
     * @param id id
     * @param name name
     * @param ruby ruby
     * @param gender gender
     * @param birthday birthday
     * @param licenses licenses
     */
    public Person(Integer id, String name, String ruby, Gender gender, Optional<LocalDate> birthday, List<License> licenses) {
        this.id = id;
        this.name = name;
        this.ruby = ruby;
        this.gender = gender;
        this.birthday = birthday;
        this.licenses = licenses;
    }

    @SuppressWarnings("javadoc")
    public static class Builder extends AbstractBuilder<Person, Builder> {

        enum Fields {
            id,
            name,
            ruby,
            gender,
            birthday,
            licenses;
        }

        public Builder() {
            super(Fields.class, Person.class);
        }
    }
}
