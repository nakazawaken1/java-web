package app.model;

import framework.annotation.Mapping;
import framework.annotation.Id;
import framework.annotation.InitialData;
import framework.annotation.Size;

/**
 * 資格
 */
@Mapping("t_license")
@InitialData(field = "id, name", value = { "1, 'ITパスポート'", "2, '基本情報'", "3, '応用情報'" })
public class License {
    /**
     * 一意キー
     */
    @Id
    public long id;
    /**
     * 資格名称
     */
    @Size(20)
    public String name;
}
