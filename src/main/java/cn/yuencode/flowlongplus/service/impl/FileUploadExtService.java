package cn.yuencode.flowlongplus.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.yuencode.flowlongplus.controller.res.FileUploadInfo;
import cn.yuencode.flowlongplus.entity.SysFileDetail;
import cn.yuencode.flowlongplus.mapper.FileDetailMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;

@Service
public class FileUploadExtService {

    @Resource
    private FileDetailMapper fileDetailMapper;

    public FileUploadInfo upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            // 1. 构建保存路径
            String basePath = "C:/fileUpload";
            java.io.File dir = new java.io.File(basePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 2. 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "") + ext;
            String path = basePath + "/" + filename;

            // 3. 保存文件到本地
            java.io.File dest = new java.io.File(path);
            file.transferTo(dest);

            // 4. 构建数据库实体
             SysFileDetail entity = new  SysFileDetail();
            entity.setFilename(filename);
            entity.setOriginalFilename(originalFilename);
            entity.setSize(file.getSize());
            entity.setBasePath(basePath);
            entity.setPath(filename);
            entity.setUrl(filename); // 这里假设通过id或filename访问
            entity.setExt(ext);
            entity.setContentType(file.getContentType());
            entity.setPlatform("local");
            // 插入数据库
            fileDetailMapper.insert(entity);

            // 5. 构建返回对象
            FileUploadInfo info = new FileUploadInfo();
            info.setId(entity.getId());
            info.setUrl(entity.getUrl());
            info.setSize(entity.getSize());
            info.setFilename(entity.getFilename());
            info.setOriginalFilename(entity.getOriginalFilename());
            info.setBasePath(entity.getBasePath());
            info.setPath(entity.getPath());
            return info;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 删除文件
     * @param sysFileDetail
     * @return
     */
    public boolean exists(SysFileDetail sysFileDetail) {
        String filePath = buildFilePath(sysFileDetail);
        File file = new File(filePath);
        return file.exists();

    }


    /**
     * 下载文件
     * @param sysFileDetail 文件实体
     * @return 文件字节数组
     */
    public byte[] download(SysFileDetail sysFileDetail) {
        String filePath = buildFilePath(sysFileDetail);
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        return FileUtil.readBytes(file);
    }

    /**
     *  构建文件路径
     * @param sysFileDetail
     * @return
     */
    private  String buildFilePath(SysFileDetail sysFileDetail) {
        String basePath = sysFileDetail.getBasePath();
        String filename = sysFileDetail.getFilename();
        return basePath + "/" + filename;
    }
}
