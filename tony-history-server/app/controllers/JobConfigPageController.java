package controllers;

import cache.CacheWrapper;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.linkedin.tony.TonyConfigurationKeys;
import com.typesafe.config.Config;
import hadoop.Configuration;
import java.util.List;
import javax.inject.Inject;
import models.JobConfig;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Controller;
import play.mvc.Result;
import utils.HdfsUtils;

import static utils.HdfsUtils.*;
import static utils.ParserUtils.*;

public class JobConfigPageController extends Controller {
  private static final ALogger LOG = Logger.of(JobConfigPageController.class);
  private final Config config;

  @Inject
  public JobConfigPageController(Config config) {
    this.config = config;
  }

  public Result index(String jobId) {
    HdfsConfiguration conf = Configuration.getHdfsConf();
    FileSystem myFs = HdfsUtils.getFileSystem(conf);
    Cache<String, List<JobConfig>> cache = CacheWrapper.getConfigCache();

    if (myFs == null) {
      return internalServerError("Failed to initialize file system");
    }

    List<JobConfig> listOfConfigs;
    Path tonyHistoryFolder = new Path(config.getString(TonyConfigurationKeys.TONY_HISTORY_LOCATION));
    listOfConfigs = cache.getIfPresent(jobId);
    if (listOfConfigs != null) {
      return ok(views.html.config.render(listOfConfigs));
    }
    List<Path> jobFolder = getJobFolders(myFs, tonyHistoryFolder, jobId);
    // There should only be 1 folder since jobId is unique
    Preconditions.checkArgument(jobFolder.size() == 1);
    listOfConfigs = parseConfig(myFs, jobFolder.get(0));
    if (listOfConfigs.size() == 0) {
      LOG.error("Failed to fetch list of configs");
      return internalServerError("Failed to fetch configuration");
    }
    cache.put(jobId, listOfConfigs);
    return ok(views.html.config.render(listOfConfigs));
  }
}
