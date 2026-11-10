package com.smartledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史标签联想结果（V2）。
 */
@Data
@AllArgsConstructor
public class TagSuggestDTO {

    private List<String> tags = new ArrayList<>();
}