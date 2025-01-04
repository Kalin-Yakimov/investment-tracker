package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.dto.DropdownItemDto;
import com.kalin.investment_tracker.dto.DropdownItemResponseDto;
import com.kalin.investment_tracker.mapper.DropdownItemMapper;
import com.kalin.investment_tracker.model.DropdownItem;
import com.kalin.investment_tracker.repository.DropdownItemRepository;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import com.kalin.investment_tracker.utils.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.kalin.investment_tracker.utils.StringUtils.formatString;

@Service
public class DropdownItemService {

    private final DropdownItemRepository dropdownItemRepository;
    private final UserRepository userRepository;
    private final DropdownItemMapper dropdownItemMapper;

    public DropdownItemService(DropdownItemRepository dropdownItemRepository, UserRepository userRepository, DropdownItemMapper dropdownItemMapper) {
        this.dropdownItemRepository = dropdownItemRepository;
        this.userRepository = userRepository;
        this.dropdownItemMapper = dropdownItemMapper;
    }

    @Transactional
    public DropdownItemResponseDto addDropdownItem(DropdownItemDto dto, Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // changing to lowercase before checking in the DB if it already exists.
        String normalizedItemName = StringUtils.normalizeString(dto.itemName());
        if (dropdownItemRepository.existsByItemNameAndUserId(dto.itemName(), userId)) {
            throw new RuntimeException("Item with this name already exists");
        }
        DropdownItem dropdownItem = dropdownItemMapper.convertToDropdownItem(dto);
        dropdownItem.setItemName(normalizedItemName); // setting the lowercase name.
        dropdownItem.setUser(user);
        DropdownItem savedItem = dropdownItemRepository.save(dropdownItem);

        return dropdownItemMapper.convertToDropdownItemResponseDto(savedItem);
    }

    public Page<DropdownItemResponseDto> getAllDropdownItems(Integer userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DropdownItem> dropdownItems = dropdownItemRepository.findAllByUserIdOrderByIdDesc(userId, pageable);
        //formating the name for each item.
        dropdownItems.forEach(item -> item.setItemName(formatString(item.getItemName())));
        return dropdownItems.map(dropdownItemMapper::convertToDropdownItemResponseDto);

    }

    public Page<DropdownItemResponseDto> getFilteredDropdownItems(Integer userId, String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DropdownItem> dropdownItems;

        if (searchTerm == null || searchTerm.isEmpty() || searchTerm.length() < 3) {
            // returns 10 results if nothing is typed in the input
            dropdownItems = dropdownItemRepository.findAllByUserIdOrderByIdDesc(userId, pageable);
        } else {
            // after 3 characters it starts filters
            dropdownItems = dropdownItemRepository.searchByUserIdAndItemNameContainingIgnoreCase(userId, searchTerm, pageable);
        }

        dropdownItems.forEach(item -> item.setItemName(formatString(item.getItemName())));
        return  dropdownItems.map(dropdownItemMapper::convertToDropdownItemResponseDto);
    }

    public void deleteById(Long id, Integer userId) {
        DropdownItem dropdownItem = dropdownItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("DropdownItem not found"));
        if (!dropdownItemRepository.existsById(id)) {
            throw new RuntimeException("DropdownItem not found");
        }
        dropdownItemRepository.deleteById(id);
    }
}
