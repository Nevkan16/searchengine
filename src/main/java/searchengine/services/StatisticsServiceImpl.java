package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteRepository siteRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = getTotalStatistics();
        List<DetailedStatisticsItem> detailed = getDetailedStatistics();

        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);
        return response;
    }

    private TotalStatistics getTotalStatistics() {
        int totalSites = sites.getSites().size();

        List<SiteEntity> siteEntities = siteRepository.findAll();
        int totalPages = siteEntities.stream().mapToInt(site -> site.getPages().size()).sum();
        int totalLemmas = siteEntities.stream().mapToInt(site -> site.getLemmas().size()).sum();
        boolean isIndexing = siteEntities.stream().anyMatch(site -> site.getStatus() == SiteEntity.Status.INDEXING);

        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites(totalSites);
        totalStatistics.setPages(totalPages);
        totalStatistics.setLemmas(totalLemmas);
        totalStatistics.setIndexing(isIndexing);

        return totalStatistics;
    }

    private List<DetailedStatisticsItem> getDetailedStatistics() {
        List<SiteEntity> siteEntities = siteRepository.findAll();

        return sites.getSites().stream().map(siteConfig -> {

            SiteEntity siteEntity = siteEntities.stream()
                    .filter(dbSite -> dbSite.getUrl().equals(siteConfig.getUrl()))
                    .findFirst()
                    .orElse(null);

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(siteConfig.getUrl());
            item.setName(siteConfig.getName());

            item.setStatus(siteEntity != null ? siteEntity.getStatus().name() : "");
            item.setStatusTime(siteEntity != null ? siteEntity.getStatusTime()
                    .atZone(ZoneId.of("Europe/Moscow")).toEpochSecond() * 1000 : 0L);
            item.setError(siteEntity != null && siteEntity.getLastError() != null ? siteEntity.getLastError() : "");
            item.setPages(siteEntity != null ? siteEntity.getPages().size() : 0);
            item.setLemmas(siteEntity != null ? siteEntity.getLemmas().size() : 0);

            return item;
        }).collect(Collectors.toList());
    }

}
