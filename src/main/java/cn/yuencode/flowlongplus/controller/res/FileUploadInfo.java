

package cn.yuencode.flowlongplus.controller.res;

import java.io.Serializable;

import lombok.Data;

@Data
public class FileUploadInfo implements Serializable {
    private String id;
    private String url;
    private Long size;
    private String filename;
    private String originalFilename;
    private String basePath;
    private String path;

    private static final long serialVersionUID = 1L;

}
