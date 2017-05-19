package app.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

import framework.AbstractBuilder;
import framework.annotation.Help;
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
@Help("個人")
public class Person {
    /**
     * primary key
     */
    @Required({ Update.class, Delete.class })
    @Id
    @Help("個人ID")
    public final Integer id;

    /**
     * display name
     */
    @Required(Save.class)
    @Size(10)
    @Help("氏名")
    public final String name;

    /**
     * furigana
     */
    @Required(Save.class)
    @Size(20)
    @Letters(Letters.KATAKANA)
    @Help("ふりがな")
    public final String ruby;

    /**
     * gender
     */
    public enum Gender implements IntSupplier {

        /**
         * unknown
         */
        UNKNOWN,

        /**
         * male
         */
        MALE,

        /**
         * female
         */
        FEMALE;

        /**
         * @return id
         */
        @Override
        public int getAsInt() {
            return ordinal();
        }
    }

    /**
     * gender
     */
    @Help("性別")
    public final Gender gender;

    /**
     * birth day
     */
    @Time(past = 130, future = 0, unit = Unit.YEARS)
    @Help("生年月日")
    public final Optional<LocalDate> birthday;

    /**
     * @return age
     */
    @Help("年齢")
    public Optional<Long> getAge() {
        return birthday.map(i -> ChronoUnit.YEARS.between(i, LocalDate.now()));
    }

    /**
     * license
     */
    @Join(table = "t_person_license", from = "id:person_id",
        to = "id:license_id"/* "SELECT * FROM license l INNER JOIN pserson_license pl ON pl.person_id = ${id} AND l.id = pl.license_id" */)
    @Help("保有資格")
    public final List<License> licenses;

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
    public static class Builder extends AbstractBuilder<Person, Builder, Builder.Fields> {
        enum Fields {
            id,
            name,
            ruby,
            gender,
            birthday,
            licenses;
        }
    }
}
