package com.pre.im;

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * 上传APK的插件
 */
public class PrePlugin implements Plugin<Project> {
    private Project project = null;

    // URL for uploading apk file
    private static final String APK_UPLOAD_URL = "http://api.pre.im/api/v1/app/upload";

    @Override
    void apply(Project project) {
        println '在这里处理上传APK任务...' + project.name
        this.project = project
        //接收外部参数
        project.extensions.create("pre", PreExtension)

        // 取得外部参数
        def has = project.android.hasProperty("applicationVariants")
        def value = project.getProperties().get('applicationVariants')

        println("has: ${has},value: ${value}")

        if (project.android.hasProperty("applicationVariants")) { // For android application.
            project.android.applicationVariants.all { variant ->
                String variantName = variant.name.capitalize()
                // Check for execution
                if (false == project.pre.enable) {
                    project.logger.error("Pre.im gradle enable is false, if you want to auto upload apk file, you should set the execute = true")
                    return
                }

                //创建一个Task
                //Task preTask = createUploadTask(variant)

                //在打包任务执行完之后，执行上传任务
                project.tasks["assemble${variantName}"].doLast {
                    println "===== assemble${variantName} doLast...========"
                    //if debug model and debugOn = false no execute upload
                    if (variantName.contains("Debug") && !project.pre.debugOn) {
                        println("Pre: the option debugOn is closed, if you want to upload apk file on debug model, you can set debugOn = true to open it")
                        return
                    }

                    if (variantName.contains("Release")) {
                        println("Pre.in: the option autoUpload is opened, it will auto upload the release to the pre platform")
                    }
                    //创建上传任务
                    createUploadTask(variant)

                    //执行上传任务
                    //uploadApk(generateUploadInfo(variant))
                }
            }
        }
    }

    /**
     * generate upload info
     * @param variant
     * @return
     */
    public UploadInfo generateUploadInfo(Object variant) {
        println "========= variant: ${variant} =========="
//        def manifestFile = variant.outputs.processManifest.manifestOutputFile[0]
        //def manifestFile = variant.outputs[0].processManifest.manifestOutputFile[0]
        def manifestFile = variant.outputs[0].processResources.manifestFile

        println("-> Manifest: " + manifestFile)
        println("VersionCode: " + variant.getVersionCode() + " VersionName: " + variant.getVersionName())

        UploadInfo uploadInfo = new UploadInfo()
        uploadInfo.user_key = project.pre.user_key;

        uploadInfo.password = project.pre.password;
        uploadInfo.update_notify = project.pre.update_notify;
        // if you not set apkFile, default get the assemble output file
        if (project.pre.file != null) {
            uploadInfo.file = project.pre.file
            println("pre: you has set the custom file")
            println("pre: your apk absolutepath :" + project.pre.file)
        } else {
            File apkFile = variant.outputs[0].outputFile
            uploadInfo.file = apkFile.getAbsolutePath()
            println("pre: the apkFile is default set to build file")
            println("pre: your apk absolutepath :" + apkFile.getAbsolutePath())
        }


        return uploadInfo
    }

    /**
     * 创建上传任务
     *
     * @param variant 编译参数
     * @return
     */
    private Task createUploadTask(Object variant) {
        String variantName = variant.name.capitalize()
        println "========创建上传任务  variantName: ${variantName} ========="

        Task uploadTask = project.tasks.create("upload${variantName}PreApkFile") {
            //使用task的方法配置
//            setGroup('pandora')
//            setDescription('upload task..')

            // if debug model and debugOn = false no execute upload
            if (variantName.contains("Debug") && !project.pre.debugOn) {
                println("Pre: the option debugOn is closed, if you want to upload apk file on debug model, you can set debugOn = true to open it")
                return
            }

            //上传APK
            println "========begin 上传APK========="
            def isSuccess = uploadApk(generateUploadInfo(variant))
            println "========end 上传APK ${isSuccess}========="
        }
        println("Pre:create upload${variantName}PreApkFile task")
        return uploadTask
    }

    /**
     *  上传apk
     * @param uploadInfo
     * @return
     */
    public boolean uploadApk(UploadInfo uploadInfo) {
        // 拼接url如：curl -F "file=@abc.apk" -F "user_key={user_key}" -F "update_notify=1" http://api.pre.im/api/v1/app/upload
        String url = APK_UPLOAD_URL
        println("Pre.im: Apk start uploading....")

        if (uploadInfo.user_key == null) {
            project.logger.error("Please set the user_key = \"xxxxx900037672xxx\"")
            return false
        }
        println("Pre:" + uploadInfo.toString())

        if (!post(url, uploadInfo.file, uploadInfo)) {
            project.logger.error("Pre: Failed to upload!")
            return false
        } else {
            println("Pre: upload apk success !!!")
            return true
        }
    }

    /**
     * 上传apk
     * @param url 地址
     * @param filePath 文件路径
     * @param uploadInfo 更新信息
     * @return
     */
    public boolean post(String url, String filePath, UploadInfo uploadInfo) {
        try {
            HttpURLConnectionUtil connectionUtil = new HttpURLConnectionUtil(url, Constants.HTTPMETHOD_POST);
            connectionUtil.addTextParameter(Constants.USER_KEY, uploadInfo.user_key);
            connectionUtil.addTextParameter(Constants.PASSWORD, uploadInfo.password);
            connectionUtil.addTextParameter(Constants.UPDATE_NOTIFY, uploadInfo.update_notify);
            connectionUtil.addFileParameter(Constants.FILE, new File(filePath));

            //上传APK到内测平台
            String result = new String(connectionUtil.post(), "UTF-8");

            println("Pre ---result: " + result)
            def data = new JsonSlurper().parseText(result)
            if (data.code == "0") {
                println("Pre --->update success: " + data.msg)
                return true
            }
        } catch (Exception e) {
            e.printStackTrace()
            println "上传APK到平台异常: ${e.getMessage()}"
        }
        return false;
    }


}
