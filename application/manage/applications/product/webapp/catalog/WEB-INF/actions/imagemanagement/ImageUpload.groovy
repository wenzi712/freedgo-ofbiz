/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import org.ofbiz.base.util.HttpRequestFileUpload
import org.ofbiz.base.util.UtilDateTime
import org.ofbiz.base.util.UtilMisc
import org.ofbiz.base.util.UtilProperties
import org.ofbiz.base.util.string.FlexibleStringExpander
import org.ofbiz.entity.condition.EntityCondition
import org.ofbiz.product.image.ScaleImage

context.nowTimestampString = UtilDateTime.nowTimestamp().toString()

// make the image file formats
imageFilenameFormat = UtilProperties.getPropertyValue('catalog', 'image.filename.format')
imageServerPath = FlexibleStringExpander.expandString(UtilProperties.getPropertyValue("catalog", "image.server.path"), context)
while(imageServerPath.endsWith("/")) imageServerPath = imageServerPath.substring(0,imageServerPath.length()-1)
imageUrlPrefix = UtilProperties.getPropertyValue('catalog', 'image.url.prefix')
context.imageFilenameFormat = imageFilenameFormat
context.imageServerPath = imageServerPath
context.imageUrlPrefix = imageUrlPrefix

filenameExpander = FlexibleStringExpander.getInstance(imageFilenameFormat)
context.imageNameSmall  = imageUrlPrefix + "/" + filenameExpander.expandString([location : 'products', type : 'small' , id : productId])
context.imageNameMedium = imageUrlPrefix + "/" + filenameExpander.expandString([location : 'products', type : 'medium', id : productId])
context.imageNameLarge  = imageUrlPrefix + "/" + filenameExpander.expandString([location : 'products', type : 'large' , id : productId])
context.imageNameDetail = imageUrlPrefix + "/" + filenameExpander.expandString([location : 'products', type : 'detail', id : productId])
context.imageNameOriginal = imageUrlPrefix + "/" + filenameExpander.expandString([location : 'products', type : 'original', id : productId])

// Start ProductContent stuff
productContent = null
if (product) {
    productContent = product.getRelated('ProductContent', null, ['productContentTypeId'])
}
context.productContent = productContent
// End ProductContent stuff

tryEntity = true
if (request.getAttribute("_ERROR_MESSAGE_")) {
    tryEntity = false
}
if (!product) {
    tryEntity = false
}

if ("true".equalsIgnoreCase((String) request.getParameter("tryEntity"))) {
    tryEntity = true
}
context.tryEntity = tryEntity

// UPLOADING STUFF
forLock = new Object()
contentType = null
String fileType = request.getParameter("upload_file_type")
if (fileType) {

    context.fileType = fileType

    fileLocation = filenameExpander.expandString([location : 'products', type : fileType, id : productId])
    filePathPrefix = ""
    filenameToUse = fileLocation
    if (fileLocation.lastIndexOf("/") != -1) {
        filePathPrefix = fileLocation.substring(0, fileLocation.lastIndexOf("/") + 1) // adding 1 to include the trailing slash
        filenameToUse = fileLocation.substring(fileLocation.lastIndexOf("/") + 1)
    }

    int i1
    if (contentType && (i1 = contentType.indexOf("boundary=")) != -1) {
        contentType = contentType.substring(i1 + 9)
        contentType = "--" + contentType
    }

    defaultFileName = filenameToUse + "_temp"
    uploadObject = new HttpRequestFileUpload()
    uploadObject.setOverrideFilename(defaultFileName)
    uploadObject.setSavePath(imageServerPath + "/" + filePathPrefix)
    uploadObject.doUpload(request)

    clientFileName = uploadObject.getFilename()
    if (clientFileName) {
        context.clientFileName = clientFileName
    }
    String fileExt = ""
    if (clientFileName && clientFileName.length() > 0) {
        if (clientFileName.lastIndexOf(".") > 0 && clientFileName.lastIndexOf(".") < clientFileName.length()) {
            filenameToUse += clientFileName.substring(clientFileName.lastIndexOf("."))
            fileExt = clientFileName.substring(clientFileName.lastIndexOf("."))
        } else {
            filenameToUse += ".jpg"
            fileExt =".jpg"
        }

        context.clientFileName = clientFileName
        context.filenameToUse = filenameToUse

        characterEncoding = request.getCharacterEncoding()
        imageUrl = imageUrlPrefix + "/" + filePathPrefix + java.net.URLEncoder.encode(filenameToUse, characterEncoding)

        try {
            file = new File(imageServerPath + "/" + filePathPrefix, defaultFileName)
            file1 = new File(imageServerPath + "/" + filePathPrefix, filenameToUse)
            try {
                file1.delete()
            } catch (Exception e) {
                System.out.println("error deleting existing file (not neccessarily a problem)")
            }
            file.renameTo(file1)
        } catch (Exception e) {
            e.printStackTrace()
        }
        String saveImagePath = imageUrlPrefix + "/" + filePathPrefix
        String saveImageKey = (saveImagePath+fileType+fileExt).substring(1)
        filePath = imageServerPath + "/" + filePathPrefix + fileType+fileExt
        println "filePath = $filePath"
        println "saveImageKey = $saveImageKey"
        dispatcher.runAsync("coverUpload",UtilMisc.toMap("filePath",filePath,"fileKey",saveImageKey))

        if (imageUrl && imageUrl.length() > 0) {
            context.imageUrl = imageUrl
            product.set(fileType + "ImageUrl", imageUrl)



            // call scaleImageInAllSize
            if (fileType.equals("original")) {

                result = ScaleImage.scaleImageInAllSize(context, filenameToUse, "main", "0","products",productId,dispatcher)

                if (result.containsKey("responseMessage") && result.get("responseMessage").equals("success")) {
                    imgMap = result.get("imageUrlMap")
                    imgMap.each() { key, value ->
                        product.set(key + "ImageUrl", value)
                    }
                }
            }

            product.store()
        }
    }
}

context.productFeatures = delegator.findList("ProductFeature", EntityCondition.makeCondition([productFeatureTypeId : "SIZE", productFeatureCategoryId : "IMAGE"]), null, null, null, false)
