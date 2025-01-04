package com.kalin.investment_tracker.mapper;

import com.kalin.investment_tracker.dto.DropdownItemDto;
import com.kalin.investment_tracker.dto.DropdownItemResponseDto;
import com.kalin.investment_tracker.model.DropdownItem;
import org.springframework.stereotype.Service;

@Service
public class DropdownItemMapper {

    public DropdownItem convertToDropdownItem(DropdownItemDto dto) {
        var convertedDropdownItem = new DropdownItem();
        convertedDropdownItem.setItemName(dto.itemName());
        convertedDropdownItem.setId(dto.id());

        return convertedDropdownItem;
    }

    public DropdownItemResponseDto convertToDropdownItemResponseDto(DropdownItem dropdownItem) {
        return new DropdownItemResponseDto(
                dropdownItem.getId(),
                dropdownItem.getItemName()
        );
    }
}
