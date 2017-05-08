package framework;

/**
 * Abstract parser
 */
public abstract class AbstractParser {

    /**
     * Space letters
     */
    protected String spaces = " \t\r\n";

    /**
     * target text
     */
    protected StringBuilder source = new StringBuilder();

    /**
     * current index
     */
    protected int index;

    /**
     * last index
     */
    protected int lastIndex;

    /**
     * @param current Current index
     * @return Trimmed index
     */
    protected int trimRight(int current) {
        while (spaces.indexOf(source.charAt(current - 1)) >= 0) {
            current--;
        }
        return current;
    }

    /**
     * skip spaces
     */
    protected void skipSpaces() {
        for (; index < lastIndex; index++) {
            if (spaces.indexOf(source.charAt(index)) < 0) {
                return;
            }
        }
    }

    /**
     * Skip until found any letters
     *
     * @param letters Letters
     * @return True if found any letter else not found
     */
    protected boolean skipUntil(char... letters) {
        for (; index < lastIndex; index++) {
            for (int letter : letters) {
                if (letter == source.charAt(index)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Eat a word
     *
     * @param word word
     * @return true:ate a word, false:not ate
     */
    protected boolean eat(String word) {
        int length = word.length();
        if (index + length <= lastIndex && source.substring(index, index + length).equals(word)) {
            index += length;
            return true;
        }
        return false;
    }

    /**
     * index of word from current index
     *
     * @param word word
     * @return index of word, -1:not found
     */
    protected int indexOf(String word) {
        return source.indexOf(word, index);
    }

    /**
     * prev word check
     *
     * @param word word
     * @return true:equals prev word, false:not equals
     */
    boolean prev(String word) {
        int length = word.length();
        return index - length >= 0 && source.substring(index - length, index).equals(word);
    }

    /**
     * @param start Start index
     * @param end End index
     * @return Sub sequence
     */
    CharSequence subSequence(int start, int end) {
        return source.subSequence(start, end);
    }

    /**
     * @param index Index
     * @return Character
     */
    char charAt(int index) {
        return source.charAt(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return source.toString();
    }

    /**
     * @param start Start index
     * @param end End index
     * @param text Replace text
     */
    public void replace(int start, int end, String text) {
        source.replace(start, end, text);
        lastIndex = source.length();
    }

    /**
     * Setup text
     * 
     * @param text Text
     */
    void set(String text) {
        source.setLength(0);
        source.append(text);
        index = 0;
        lastIndex = source.length();
    }
}
