package com.yushang.website.vod.web.action.admin

import java.io.{ ByteArrayInputStream, File, FileInputStream }

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.api.action.ServletSupport
import org.beangle.webmvc.api.annotation.{ ignore, mapping, param }
import org.beangle.webmvc.api.view.{ Stream, View }
import org.beangle.webmvc.execution.Handler
import org.springframework.util.FileCopyUtils

import com.yushang.website.vod.core.model.{ Nav, Video }
import javax.servlet.http.Part

class VideoAction extends VodBackSupport[Video] with ServletSupport {

  override protected def indexSetting(): Unit = {
    super.indexSetting
    put("navs", entityDao.getAll(classOf[Nav]))
  }

  override protected def editSetting(video: Video): Unit = {
    indexSetting
  }

  def imageUpload(): View = {
    val name = System.currentTimeMillis() + ""

    get("image", classOf[Part]) match {
      case Some(part) =>
        val image: File = new File(master.resourceDir + "/temp/" + name)
        if (image.getParentFile.exists()) {
          get("imageUrl") match {
            case Some(imageUrl) =>
              if (imageUrl.trim.length() > 0) {
                new File(master.resourceDir + "/temp/" + imageUrl).delete
              }
            case None =>
          }
        } else {
          image.getParentFile.mkdirs
        }
        if (image.exists()) {
          image.delete
        }
        FileCopyUtils.copy(FileCopyUtils.copyToByteArray(part.getInputStream), image)

        put("imageName", part.getSubmittedFileName)
        put("imageUrl", name)
      case None =>
    }

    forward()
  }

  @mapping("imagePreview/{imageUrl}")
  def imagePreview(@param("imageUrl") imageUrl: String, @param("imageName") imageName: String): View = {
    val urlSections = Strings.split(imageName, ".")
    Stream(new ByteArrayInputStream(FileCopyUtils.copyToByteArray(new File(master.resourceDir + "/temp/" + imageUrl))), "image/" + urlSections(urlSections.length - 1), imageName)
  }

  @mapping("image/{id}")
  def image(@param("id") id: String): View = {
    val video = entityDao.get(classOf[Video], id.toLong)
    val urlSections = Strings.split(video.imageName, ".")
    Stream(new ByteArrayInputStream(FileCopyUtils.copyToByteArray(new File(master.resourceDir + "/" + video.imageUrl))), "image/" + urlSections(urlSections.length - 1), video.imageName)
  }

  def videoUpload(): View = {
    val name = System.currentTimeMillis() + ""

    get("video", classOf[Part]) match {
      case Some(part) =>
        val video: File = new File(master.resourceDir + "/temp/" + name)
        if (video.getParentFile.exists()) {
          get("videoUrl") match {
            case Some(videoUrl) =>
              if (videoUrl.trim.length() > 0) {
                new File(master.resourceDir + "/temp/" + videoUrl).delete
              }
            case None =>
          }
        } else {
          video.getParentFile.mkdirs
        }
        if (video.exists()) {
          video.delete
        }
        FileCopyUtils.copy(FileCopyUtils.copyToByteArray(part.getInputStream), video)

        put("videoName", part.getSubmittedFileName)
        put("videoUrl", name)
      case None =>
    }

    forward()
  }

  @mapping("videoPreview/{videoUrl}")
  def videoPreview(@param("videoUrl") videoUrl: String, @param("videoName") videoName: String): View = {
    val urlSections = Strings.split(videoName, ".")
    Stream(new ByteArrayInputStream(FileCopyUtils.copyToByteArray(new File(master.resourceDir + "/temp/" + videoUrl))), "application/x-shockwave-flash", videoName)
  }

  @mapping("video/{id}")
  def video(@param("id") id: String): View = {
    val video = entityDao.get(classOf[Video], id.toLong)
    val urlSections = Strings.split(video.videoName, ".")
    //    Stream(new ByteArrayInputStream(FileCopyUtils.copyToByteArray(new File(master.resourceDir + "/" + video.videoUrl))), "video/" + urlSections(urlSections.length - 1), video.videoName)

    response.setContentType("video/" + urlSections(urlSections.length - 1))
    try {
      FileCopyUtils.copy(new FileInputStream(new File(master.resourceDir + "/" + video.videoUrl)), response.getOutputStream)
      response.getOutputStream.flush
    } catch {
      case e: Exception => logger.error(e.getMessage) // 为了不在控制台输出不影响正常运行的报错
    }

    null
  }

  override protected def saveAndRedirect(video: Video): View = {
    // 清理临时资源
    val tempImg: File = new File(master.resourceDir + "/temp/" + video.imageUrl)
    if (tempImg.exists()) {
      val image: File = new File(master.resourceDir + "/" + video.imageUrl)
      FileCopyUtils.copy(tempImg, image)
      tempImg.delete
    }
    val tempV: File = new File(master.resourceDir + "/temp/" + video.videoUrl)
    if (tempV.exists()) {
      val vd: File = new File(master.resourceDir + "/" + video.videoUrl)
      FileCopyUtils.copy(tempV, vd)
      tempV.delete
    }

    if (video.persisted) {
      val dbVideo = entityDao.get(classOf[Video], video.id)

      // 清理历史资源
      if (video.imageUrl != dbVideo.imageUrl) {
        val hisImg: File = new File(master.resourceDir + "/" + dbVideo.imageUrl)
        if (hisImg.exists()) {
          hisImg.delete
        }
      }
      if (video.videoUrl != dbVideo.videoUrl) {
        val hisV: File = new File(master.resourceDir + "/" + dbVideo.videoUrl)
        if (hisV.exists()) {
          hisV.delete
        }
      }
    }
    video.videoUrl = video.videoName
    val videos = Collections.newBuffer[Video]
    videos += video
    videos ++= updateIndexNo(video)
    try {
      saveOrUpdate(videos)
      redirect("search", "info.save.success")
    } catch {
      case e: Exception => {
        val redirectTo = Handler.mapping.method.getName match {
          case "save" => "editNew"
          case "update" => "edit"
        }
        logger.info("saveAndRedirect failure", e)
        redirect(redirectTo, "info.save.failure")
      }
    }
  }

  private final def updateIndexNo(video: Video): Iterable[Video] = {
    var indexNo = video.indexNo

    val builder = OqlBuilder.from(classOf[Video], "video")
    if (video.persisted) {
      builder.where("video.id != :id", video.id)
    }
    builder.where("video.nav = :nav", video.nav)
    builder.where("video.indexNo >= :indexNo", indexNo)
    builder.orderBy("video.indexNo")
    val afters = entityDao.search(builder)

    for (after <- afters if indexNo == after.indexNo) {
      after.indexNo += 1
      indexNo = after.indexNo
    }

    afters
  }

  @ignore
  override protected def removeAndRedirect(videos: Seq[Video]): View = {
    try {
      remove(videos)
      for (video <- videos) {
        println(video.imageUrl)
        new File(master.resourceDir + "/" + video.imageUrl).delete
        new File(master.resourceDir + "/" + video.videoUrl).delete
      }
      redirect("search", "info.remove.success")
    } catch {
      case e: Exception => {
        logger.info("removeAndForwad failure", e)
        redirect("search", "info.delete.failure")
      }
    }
  }
}