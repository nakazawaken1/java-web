package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * content type
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Content {

    /**
     * @return content mime type at response
     */
    String[] value() default {};

    /**
     * text/plain
     */
    String TEXT = "text/plain";

    /**
     * text/html
     */
    String HTML = "text/html";

    /**
     * application/xhtml+xml
     */
    String XHTML = "application/xhtml+xml";

    /**
     * application/xml
     */
    String XML = "application/xml";

    /**
     * application/yml
     */
    String YML = "application/yml";

    /**
     * application/json
     */
    String JSON = "application/json";

    /**
     * text/javascript
     */
    String JS = "text/javascript";

    /**
     * text/css
     */
    String CSS = "text/css";

    /**
     * text/csv
     */
    String CSV = "text/csv";

    /**
     * text/tab-separated-values
     */
    String TSV = "text/tab-separated-values";

    /**
     * application/pdf
     */
    String PDF = "application/pdf";

    /**
     * image/gif
     */
    String GIF = "image/gif";

    /**
     * image/jpeg
     */
    String JPEG = "image/jpeg";

    /**
     * image/png
     */
    String PNG = "image/png";

    /**
     * video/mpeg
     */
    String MPEG = "video/mpeg";

    /**
     * application/octet-stream
     */
    String OCTET = "application/octet-stream";

    /**
     * application/vnd.ms-excel
     */
    String XLS = "application/vnd.ms-excel";

    /**
     * application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
     */
    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * application/vnd.ms-excel.sheet.macroenabled.12
     */
    String XLSM = "application/vnd.ms-excel.sheet.macroenabled.12";

    /**
     * application/msword
     */
    String DOC = "application/msword";

    /**
     * application/vnd.openxmlformats-officedocument.wordprocessingml.document
     */
    String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
}
