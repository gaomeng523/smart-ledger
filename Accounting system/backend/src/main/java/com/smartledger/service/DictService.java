package com.smartledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartledger.dto.response.CategoryNodeDTO;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.dto.response.DictResponse;
import com.smartledger.entity.Category;
import com.smartledger.entity.PayChannel;
import com.smartledger.mapper.CategoryMapper;
import com.smartledger.mapper.PayChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 字典服务：分类与渠道。变化频率低，使用本地 Caffeine 缓存 10 分钟。
 * 分类为两级结构：一级分类（level=1）下挂二级分类（level=2，parentCode 指向一级）。
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private static final String ALL_KEY = "all";

    private final CategoryMapper categoryMapper;
    private final PayChannelMapper payChannelMapper;

    private final Cache<String, List<DictItemDTO>> channelCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(4)
            .build();

    private final Cache<String, List<DictItemDTO>> categoryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(4)
            .build();

    /** 全部启用的支付渠道 */
    public List<DictItemDTO> listChannels() {
        return channelCache.get(ALL_KEY, k -> {
            List<PayChannel> list = payChannelMapper.selectList(new LambdaQueryWrapper<PayChannel>()
                    .eq(PayChannel::getStatus, 1)
                    .orderByAsc(PayChannel::getSortNo));
            return list.stream()
                    .map(c -> new DictItemDTO(c.getCode(), c.getName(), null, c.getSortNo(), null, null, null))
                    .collect(Collectors.toList());
        });
    }

    /** 全部启用的分类（先一级后二级） */
    public List<DictItemDTO> listCategories() {
        return categoryCache.get(ALL_KEY, k -> {
            List<Category> list = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                    .eq(Category::getStatus, 1)
                    .orderByAsc(Category::getBillType)
                    .orderByAsc(Category::getLevel)
                    .orderByAsc(Category::getSortNo));
            return list.stream()
                    .map(c -> new DictItemDTO(
                            c.getCode(),
                            c.getName(),
                            c.getBillType(),
                            c.getSortNo(),
                            c.getParentCode(),
                            c.getLevel() == null ? 1 : c.getLevel(),
                            c.getIcon()))
                    .collect(Collectors.toList());
        });
    }

    /** 两级分类树（一级 + 其下二级） */
    public List<CategoryNodeDTO> categoryTree() {
        List<DictItemDTO> all = listCategories();
        Map<String, CategoryNodeDTO> parents = new LinkedHashMap<>();

        for (DictItemDTO item : all) {
            if (isLevel1(item)) {
                CategoryNodeDTO node = new CategoryNodeDTO();
                node.setCode(item.getCode());
                node.setName(item.getName());
                node.setBillType(item.getBillType());
                node.setIcon(item.getIcon());
                node.setChildren(new ArrayList<>());
                parents.put(item.getCode(), node);
            }
        }
        for (DictItemDTO item : all) {
            if (!isLevel1(item)) {
                CategoryNodeDTO parent = parents.get(item.getParentCode());
                if (parent != null) {
                    parent.getChildren().add(item);
                }
            }
        }
        return new ArrayList<>(parents.values());
    }

    /** code -> 所属一级分类 code（一级自分） */
    public Map<String, String> categoryParentMap() {
        return listCategories().stream()
                .collect(Collectors.toMap(
                        DictItemDTO::getCode,
                        item -> item.getParentCode() == null ? item.getCode() : item.getParentCode(),
                        (a, b) -> a));
    }

    public DictResponse getAll() {
        DictResponse response = new DictResponse();
        response.setChannels(listChannels());
        response.setCategories(listCategories());
        response.setCategoryTree(categoryTree());
        return response;
    }

    /** code -> 渠道名 */
    public Map<String, String> channelNameMap() {
        return listChannels().stream()
                .collect(Collectors.toMap(DictItemDTO::getCode, DictItemDTO::getName, (a, b) -> a));
    }

    /** code -> 分类名 */
    public Map<String, String> categoryNameMap() {
        return listCategories().stream()
                .collect(Collectors.toMap(DictItemDTO::getCode, DictItemDTO::getName, (a, b) -> a));
    }

    public String channelName(String code) {
        return channelNameMap().getOrDefault(code, code);
    }

    public String categoryName(String code) {
        return categoryNameMap().getOrDefault(code, code);
    }

    public DictItemDTO getChannel(String code) {
        if (code == null) {
            return null;
        }
        return listChannels().stream()
                .filter(item -> code.equalsIgnoreCase(item.getCode()))
                .findFirst()
                .orElse(null);
    }

    public DictItemDTO getCategory(String code) {
        if (code == null) {
            return null;
        }
        return listCategories().stream()
                .filter(item -> code.equalsIgnoreCase(item.getCode()))
                .findFirst()
                .orElse(null);
    }

    /** 按名称或编码匹配渠道，用于 AI 结果归一化 */
    public DictItemDTO matchChannel(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.isBlank()) {
            return null;
        }
        String target = nameOrCode.trim();
        Function<DictItemDTO, Boolean> hit = item ->
                target.equals(item.getName()) || target.equalsIgnoreCase(item.getCode());
        return listChannels().stream().filter(hit::apply).findFirst().orElse(null);
    }

    /**
     * 按名称或编码匹配指定收支类型下的分类，用于 AI 结果归一化。
     * 先精确匹配；未命中再做包含匹配，优先二级分类、其次名称更长者，
     * 这样「买菜」能落到「买菜食材」，而不会落到一级「买菜餐饮」。
     */
    public DictItemDTO matchCategory(String nameOrCode, Integer billType) {
        if (nameOrCode == null || nameOrCode.isBlank()) {
            return null;
        }
        String target = nameOrCode.trim();
        List<DictItemDTO> candidates = listCategories().stream()
                .filter(item -> billType == null || billType.equals(item.getBillType()))
                .collect(Collectors.toList());

        DictItemDTO exact = candidates.stream()
                .filter(item -> target.equals(item.getName()) || target.equalsIgnoreCase(item.getCode()))
                .findFirst()
                .orElse(null);
        if (exact != null) {
            return exact;
        }

        return candidates.stream()
                .filter(item -> target.contains(item.getName()) || item.getName().contains(target))
                .max(Comparator.comparingInt(item -> item.getName().length() * 2 + (isLevel1(item) ? 0 : 1)))
                .orElse(null);
    }

    /** 清空缓存（字典维护后调用） */
    public void evictAll() {
        channelCache.invalidateAll();
        categoryCache.invalidateAll();
    }

    private boolean isLevel1(DictItemDTO item) {
        return item.getLevel() == null || item.getLevel() == 1;
    }
}
