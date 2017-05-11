package framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import difflib.Chunk;
import difflib.Delta;
import difflib.myers.Equalizer;
import difflib.myers.MyersDiff;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Diff
 * 
 * @param <T> Diff element type
 */
public class Diff<T> {
    @SuppressWarnings("javadoc")
    public enum Type {
        EQUAL,
        INSERT,
        DELETE,
        CHANGE,
        SKIP;
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * Diff type
     */
    public Type type;
    /**
     * before index
     */
    public Optional<Integer> beforeIndex;
    /**
     * before
     */
    public Optional<T> before;
    /**
     * after index
     */
    public Optional<Integer> afterIndex;
    /**
     * after
     */
    public Optional<T> after;

    /**
     * @return before index as text
     */
    public String getBeforeIndexText() {
        return type == Type.SKIP ? getBeforeText() : beforeIndex.map(String::valueOf).orElse("");
    }

    /**
     * @return before as text
     */
    public String getBeforeText() {
        return before.map(String::valueOf).orElse("");
    }

    /**
     * @return after index as text
     */
    public String getAfterIndexText() {
        return type == Type.SKIP ? getAfterText() : afterIndex.map(String::valueOf).orElse("");
    }

    /**
     * @return after as text
     */
    public String getAfterText() {
        return after.map(String::valueOf).orElse("");
    }

    /**
     * default equalizer
     * 
     * @param <T> element type
     * @return equalizer
     */
    public static <T> Equalizer<T> DEFAULT() {
        return (a, b) -> a.equals(b);
    }

    /**
     * ignore space equalizer
     */
    public static final Equalizer<String> IGNORE_SPACE = (i, j) -> i.replaceAll("\\s{2,}", " ").equals(j.replaceAll("\\s{2,}", " "));

    /**
     * HTML escape editor
     */
    public static final Consumer<Diff<String>> ESCAPE = d -> {
        d.before = d.before.map(Tool::htmlEscape);
        d.after = d.after.map(Tool::htmlEscape);
    };

    /**
     * @param start start index
     * @param end end index
     * @param tag tag name
     * @return insert tag function
     */
    static Function<String, String> insertTag(int start, int end, String tag) {
        return s -> s.substring(0, start) + "<" + tag + ">" + s.substring(start, end) + "</" + tag + ">" + s.substring(end);
    }

    /**
     * @param tag letter decoration tag(ex: b)
     * @param limit mark limit
     * @return editor
     */
    public static Consumer<Diff<String>> INLINE(String tag, int limit) {
        return d -> {
            d.before = d.before.map(Tool::htmlEscape);
            d.after = d.after.map(Tool::htmlEscape);
            if (d.type != Type.CHANGE) {
                return;
            }
            List<Delta<Character>> deltas = new MyersDiff<Character>(DEFAULT())
                    .diff(d.before.map(Tool::toCharacterArray).orElse(new Character[] {}), d.after.map(Tool::toCharacterArray).orElse(new Character[] {}))
                    .getDeltas();
            if (deltas.isEmpty()) {
                return;
            }
            Function<Chunk<Character>, Integer> getEnd = i -> i.getPosition() + i.getLines().size();
            if (deltas.size() > limit) {
                Delta<Character> top = deltas.get(0);
                Delta<Character> last = deltas.get(deltas.size() - 1);
                {
                    int start = top.getOriginal().getPosition();
                    int end = Tool.val(last.getOriginal(), getEnd);
                    if (start < end) {
                        d.before = d.before.map(insertTag(start, end, tag));
                    }
                }
                {
                    int start = top.getRevised().getPosition();
                    int end = Tool.val(last.getRevised(), getEnd);
                    if (start < end) {
                        d.after = d.after.map(insertTag(start, end, tag));
                    }
                }
            } else {
                Collections.reverse(deltas);
                for (Delta<Character> delta : deltas) {
                    {
                        int start = delta.getOriginal().getPosition();
                        int end = Tool.val(delta.getOriginal(), getEnd);
                        if (start < end) {
                            d.before = d.before.map(insertTag(start, end, tag));
                        }
                    }
                    {
                        int start = delta.getRevised().getPosition();
                        int end = Tool.val(delta.getRevised(), getEnd);
                        if (start < end) {
                            d.after = d.after.map(insertTag(start, end, tag));
                        }
                    }
                }
            }
        };
    }

    /**
     * @param size tab size
     * @return editor
     */
    public static Consumer<Diff<String>> TAB(int size) {
        return d -> {
            String tab = Collections.nCopies(size, "&nbsp;").stream().collect(Collectors.joining());
            Function<String, String> f = s -> s.replace("\t", tab).replaceAll("[ ]{2}", "&nbsp;&nbsp;");
            d.before = d.before.map(f);
            d.after = d.after.map(f);
        };
    }

    /**
     * @param type Diff element type
     * @param beforeIndex before line number
     * @param before before line text
     * @param afterIndex after line number
     * @param after after line text
     */
    @SuppressFBWarnings({ "URF_UNREAD_FIELD", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD" })
    public Diff(Type type, Optional<Integer> beforeIndex, Optional<T> before, Optional<Integer> afterIndex, Optional<T> after) {
        this.type = type;
        this.beforeIndex = beforeIndex;
        this.before = before;
        this.afterIndex = afterIndex;
        this.after = after;
    }

    /**
     * @param <T> Diff type
     * @param before before
     * @param after after
     * @param equalizer equalizer(default Diff.DEFAULT())
     * @param editor editor(default null)
     * @return diff
     */
    public static <T> List<Diff<T>> diff(T[] before, T[] after, Equalizer<T> equalizer, Consumer<Diff<T>> editor) {
        List<Diff<T>> list = new ArrayList<>();
        int currentB = 0, currentA = 0;
        for (Delta<T> delta : new MyersDiff<T>(equalizer).diff(before, after).getDeltas()) {
            Chunk<T> b = delta.getOriginal();
            Chunk<T> a = delta.getRevised();
            List<T> bLines = b.getLines();
            List<T> aLines = a.getLines();
            for (int i = 0, startB = b.getPosition(), startA = a.getPosition(), maxB = b.size(), maxA = a.size(), endB = startB + maxB, endA = startA
                    + maxA; startB < endB || startA < endA; i++, startB++, startA++) {
                for (; currentB < before.length && currentA < after.length && currentB < startB && currentA < startA; currentB++, currentA++) {
                    list.add(Tool.peek(new Diff<>(Type.EQUAL, Optional.of(currentB + 1), Optional.of(before[currentB]), Optional.of(currentA + 1),
                            Optional.of(after[currentA])), editor));
                }
                Type type = Type.valueOf(delta.getType().name());
                if (type == Type.CHANGE) {
                    if (i >= maxB) {
                        type = Type.INSERT;
                    } else if (i >= maxA) {
                        type = Type.DELETE;
                    }
                }
                boolean hasB = i < maxB;
                boolean hasA = i < maxA;
                list.add(Tool.peek(new Diff<>(type, Optional.of(currentB + 1).filter(n -> hasB), Tool.of(hasB ? bLines.get(i) : null),
                        Optional.of(currentA + 1).filter(n -> hasA), Tool.of(hasA ? aLines.get(i) : null)), editor));
                if (currentB < endB) {
                    currentB++;
                }
                if (currentA < endA) {
                    currentA++;
                }
            }
        }
        for (int endB = before.length, endA = after.length, end = Math.max(endB, endA); currentB < end; currentB++, currentA++) {
            boolean hasB = currentB < endB;
            boolean hasA = currentA < endA;
            Type type = hasB && hasA ? Type.EQUAL : hasB ? Type.DELETE : Type.INSERT;
            list.add(Tool.peek(new Diff<>(type, Optional.of(currentB + 1).filter(n -> hasB), Tool.of(hasB ? before[currentB] : null),
                    Optional.of(currentA + 1).filter(n -> hasA), Tool.of(hasA ? after[currentA] : null)), editor));
        }
        return list;
    }

    /**
     * @param args not use
     */
    public static void main(String[] args) {
        System.out.print(
                Tool.dump(Diff.diff(Tool.htmlEscape("  <!-- Prevent memory leaks due to use of particular java/javax APIs-->").chars().mapToObj(i -> (char) i)
                        .toArray(Character[]::new), Tool.htmlEscape("   -->").chars().mapToObj(i -> (char) i).toArray(Character[]::new), DEFAULT(), null)));
    }

    /**
     * @param <T> element type
     * @param diffs target
     * @param lines rest lines(no compact if less then 0)
     * @param skipContent skip line content
     * @return compacted diffs
     */
    public static <T> List<Diff<T>> compact(List<Diff<T>> diffs, int lines, T skipContent) {
        if (lines <= 0) {
            return diffs;
        }
        SortedSet<Integer> picks = new TreeSet<>();
        int max = diffs.size();
        int i = 0;
        for (Diff<T> diff : diffs) {
            if (diff.type != Type.EQUAL) {
                for (int j = Math.max(0, i - lines), j2 = Math.min(i + lines + 1, max); j < j2; j++) {
                    picks.add(j);
                }
            }
            i++;
        }
        List<Diff<T>> result = new ArrayList<>();
        int pick0 = -1;
        for (int pick : picks) {
            if (pick0 + 1 != pick) {
                result.add(new Diff<>(Type.SKIP, Optional.empty(), Tool.of(skipContent), Optional.empty(), Tool.of(skipContent)));
            }
            pick0 = pick;
            result.add(diffs.get(pick));
        }
        return result;
    }
}
