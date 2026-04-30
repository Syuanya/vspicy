package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.admin.dto.DictItemCommand;
import com.vspicy.admin.dto.DictItemView;
import com.vspicy.admin.dto.DictOverviewView;
import com.vspicy.admin.dto.DictTypeCommand;
import com.vspicy.admin.dto.DictTypeView;
import com.vspicy.admin.entity.SysDictItem;
import com.vspicy.admin.entity.SysDictType;
import com.vspicy.admin.mapper.SysDictItemMapper;
import com.vspicy.admin.mapper.SysDictTypeMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class DictionaryService {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_:-]{2,63}$");
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;
    private final ObjectMapper objectMapper;

    public DictionaryService(SysDictTypeMapper dictTypeMapper,
                             SysDictItemMapper dictItemMapper,
                             ObjectMapper objectMapper) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictItemMapper = dictItemMapper;
        this.objectMapper = objectMapper;
    }

    public DictOverviewView overview() {
        Long typeCount = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>());
        Long enabledTypeCount = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getStatus, 1));
        Long disabledTypeCount = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getStatus, 0));
        Long itemCount = dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>());
        Long enabledItemCount = dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getStatus, 1));
        Long disabledItemCount = dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getStatus, 0));
        return new DictOverviewView(
                safe(typeCount),
                safe(enabledTypeCount),
                safe(disabledTypeCount),
                safe(itemCount),
                safe(enabledItemCount),
                safe(disabledItemCount)
        );
    }

    public List<DictTypeView> listTypes(String keyword, Integer status, Integer limit) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .orderByAsc(SysDictType::getTypeCode)
                .last("LIMIT " + safeLimit(limit));
        if (status != null) {
            wrapper.eq(SysDictType::getStatus, normalizeStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item.like(SysDictType::getTypeCode, value)
                    .or()
                    .like(SysDictType::getTypeName, value)
                    .or()
                    .like(SysDictType::getDescription, value));
        }
        return dictTypeMapper.selectList(wrapper).stream().map(this::toTypeView).toList();
    }

    public DictTypeView getType(Long id) {
        return toTypeView(mustGetType(id));
    }

    @Transactional
    public DictTypeView createType(DictTypeCommand command) {
        validateType(command, true);
        String typeCode = command.typeCode().trim();
        SysDictType existed = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTypeCode, typeCode)
                .last("LIMIT 1"));
        if (existed != null) {
            throw new BizException("字典类型编码已存在");
        }
        SysDictType type = new SysDictType();
        applyType(type, command, true);
        dictTypeMapper.insert(type);
        return toTypeView(type);
    }

    @Transactional
    public DictTypeView updateType(Long id, DictTypeCommand command) {
        SysDictType type = mustGetType(id);
        ensureEditable(type.getEditable(), "该字典类型为系统内置，不允许编辑");
        validateType(command, false);
        if (command != null && command.typeCode() != null && !command.typeCode().isBlank()) {
            String typeCode = command.typeCode().trim();
            SysDictType existed = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                    .eq(SysDictType::getTypeCode, typeCode)
                    .ne(SysDictType::getId, id)
                    .last("LIMIT 1"));
            if (existed != null) {
                throw new BizException("字典类型编码已存在");
            }
        }
        String oldCode = type.getTypeCode();
        applyType(type, command, false);
        dictTypeMapper.updateById(type);
        if (oldCode != null && !oldCode.equals(type.getTypeCode())) {
            syncItemTypeCode(oldCode, type.getTypeCode());
        }
        return toTypeView(mustGetType(id));
    }

    @Transactional
    public DictTypeView enableType(Long id) {
        SysDictType type = mustGetType(id);
        type.setStatus(1);
        dictTypeMapper.updateById(type);
        return toTypeView(type);
    }

    @Transactional
    public DictTypeView disableType(Long id) {
        SysDictType type = mustGetType(id);
        type.setStatus(0);
        dictTypeMapper.updateById(type);
        return toTypeView(type);
    }

    @Transactional
    public void deleteType(Long id) {
        SysDictType type = mustGetType(id);
        ensureEditable(type.getEditable(), "该字典类型为系统内置，不允许删除");
        Long itemCount = dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, type.getTypeCode()));
        if (itemCount != null && itemCount > 0) {
            throw new BizException("该字典类型下还有字典项，请先删除字典项");
        }
        dictTypeMapper.deleteById(id);
    }

    public List<DictItemView> listItems(String typeCode, String keyword, Integer status, Integer limit) {
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<SysDictItem>()
                .orderByAsc(SysDictItem::getTypeCode)
                .orderByAsc(SysDictItem::getSortNo)
                .orderByAsc(SysDictItem::getId)
                .last("LIMIT " + safeLimit(limit));
        if (typeCode != null && !typeCode.isBlank()) {
            wrapper.eq(SysDictItem::getTypeCode, typeCode.trim());
        }
        if (status != null) {
            wrapper.eq(SysDictItem::getStatus, normalizeStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item.like(SysDictItem::getItemLabel, value)
                    .or()
                    .like(SysDictItem::getItemValue, value)
                    .or()
                    .like(SysDictItem::getRemark, value));
        }
        return dictItemMapper.selectList(wrapper).stream().map(this::toItemView).toList();
    }

    public DictItemView getItem(Long id) {
        return toItemView(mustGetItem(id));
    }

    @Transactional
    public DictItemView createItem(DictItemCommand command) {
        validateItem(command, true);
        String typeCode = command.typeCode().trim();
        ensureTypeExists(typeCode);
        String itemValue = command.itemValue().trim();
        SysDictItem existed = dictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getItemValue, itemValue)
                .last("LIMIT 1"));
        if (existed != null) {
            throw new BizException("同一字典类型下字典值已存在");
        }
        SysDictItem item = new SysDictItem();
        applyItem(item, command, true);
        dictItemMapper.insert(item);
        return toItemView(item);
    }

    @Transactional
    public DictItemView updateItem(Long id, DictItemCommand command) {
        SysDictItem item = mustGetItem(id);
        ensureEditable(item.getEditable(), "该字典项为系统内置，不允许编辑");
        validateItem(command, false);
        String typeCode = command != null && command.typeCode() != null && !command.typeCode().isBlank()
                ? command.typeCode().trim()
                : item.getTypeCode();
        String itemValue = command != null && command.itemValue() != null && !command.itemValue().isBlank()
                ? command.itemValue().trim()
                : item.getItemValue();
        ensureTypeExists(typeCode);
        SysDictItem existed = dictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getItemValue, itemValue)
                .ne(SysDictItem::getId, id)
                .last("LIMIT 1"));
        if (existed != null) {
            throw new BizException("同一字典类型下字典值已存在");
        }
        applyItem(item, command, false);
        dictItemMapper.updateById(item);
        return toItemView(mustGetItem(id));
    }

    @Transactional
    public DictItemView enableItem(Long id) {
        SysDictItem item = mustGetItem(id);
        item.setStatus(1);
        dictItemMapper.updateById(item);
        return toItemView(item);
    }

    @Transactional
    public DictItemView disableItem(Long id) {
        SysDictItem item = mustGetItem(id);
        item.setStatus(0);
        dictItemMapper.updateById(item);
        return toItemView(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        SysDictItem item = mustGetItem(id);
        ensureEditable(item.getEditable(), "该字典项为系统内置，不允许删除");
        dictItemMapper.deleteById(id);
    }

    private void validateType(DictTypeCommand command, boolean create) {
        if (command == null) {
            throw new BizException("字典类型内容不能为空");
        }
        if (create || command.typeCode() != null) {
            if (command.typeCode() == null || command.typeCode().isBlank()) {
                throw new BizException("字典类型编码不能为空");
            }
            if (!CODE_PATTERN.matcher(command.typeCode().trim()).matches()) {
                throw new BizException("字典类型编码只能包含小写字母、数字、下划线、冒号和短横线，长度为 3-64");
            }
        }
        if (create || command.typeName() != null) {
            if (command.typeName() == null || command.typeName().isBlank()) {
                throw new BizException("字典类型名称不能为空");
            }
        }
    }

    private void validateItem(DictItemCommand command, boolean create) {
        if (command == null) {
            throw new BizException("字典项内容不能为空");
        }
        if (create || command.typeCode() != null) {
            if (command.typeCode() == null || command.typeCode().isBlank()) {
                throw new BizException("字典类型编码不能为空");
            }
            if (!CODE_PATTERN.matcher(command.typeCode().trim()).matches()) {
                throw new BizException("字典类型编码格式不合法");
            }
        }
        if (create || command.itemLabel() != null) {
            if (command.itemLabel() == null || command.itemLabel().isBlank()) {
                throw new BizException("字典项名称不能为空");
            }
        }
        if (create || command.itemValue() != null) {
            if (command.itemValue() == null || command.itemValue().isBlank()) {
                throw new BizException("字典项值不能为空");
            }
            if (command.itemValue().trim().length() > 128) {
                throw new BizException("字典项值长度不能超过 128");
            }
        }
        if (command.extraJson() != null && !command.extraJson().isBlank()) {
            validateJson(command.extraJson());
        }
    }

    private void applyType(SysDictType type, DictTypeCommand command, boolean create) {
        if (create || command.typeCode() != null) {
            type.setTypeCode(command.typeCode().trim());
        }
        if (create || command.typeName() != null) {
            type.setTypeName(command.typeName() == null ? null : command.typeName().trim());
        }
        if (create || command.description() != null) {
            type.setDescription(command.description());
        }
        if (create || command.editable() != null) {
            type.setEditable(Boolean.FALSE.equals(command.editable()) ? 0 : 1);
        }
        if (create || command.status() != null) {
            type.setStatus(command.status() == null ? 1 : normalizeStatus(command.status()));
        }
    }

    private void applyItem(SysDictItem item, DictItemCommand command, boolean create) {
        if (create || command.typeCode() != null) {
            item.setTypeCode(command.typeCode().trim());
        }
        if (create || command.itemLabel() != null) {
            item.setItemLabel(command.itemLabel() == null ? null : command.itemLabel().trim());
        }
        if (create || command.itemValue() != null) {
            item.setItemValue(command.itemValue() == null ? null : command.itemValue().trim());
        }
        if (create || command.sortNo() != null) {
            item.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        }
        if (create || command.cssClass() != null) {
            item.setCssClass(command.cssClass());
        }
        if (create || command.extraJson() != null) {
            item.setExtraJson(command.extraJson());
        }
        if (create || command.editable() != null) {
            item.setEditable(Boolean.FALSE.equals(command.editable()) ? 0 : 1);
        }
        if (create || command.status() != null) {
            item.setStatus(command.status() == null ? 1 : normalizeStatus(command.status()));
        }
        if (create || command.remark() != null) {
            item.setRemark(command.remark());
        }
    }

    private SysDictType mustGetType(Long id) {
        if (id == null) {
            throw new BizException("字典类型 ID 不能为空");
        }
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException(404, "字典类型不存在");
        }
        return type;
    }

    private SysDictItem mustGetItem(Long id) {
        if (id == null) {
            throw new BizException("字典项 ID 不能为空");
        }
        SysDictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "字典项不存在");
        }
        return item;
    }

    private void ensureTypeExists(String typeCode) {
        SysDictType type = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTypeCode, typeCode)
                .last("LIMIT 1"));
        if (type == null) {
            throw new BizException("字典类型不存在");
        }
    }

    private void syncItemTypeCode(String oldCode, String newCode) {
        List<SysDictItem> items = dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, oldCode));
        for (SysDictItem item : items) {
            item.setTypeCode(newCode);
            dictItemMapper.updateById(item);
        }
    }

    private int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }

    private int safeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > MAX_LIMIT ? DEFAULT_LIMIT : limit;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private void ensureEditable(Integer editable, String message) {
        if (Integer.valueOf(0).equals(editable)) {
            throw new BizException(message);
        }
    }

    private void validateJson(String value) {
        try {
            objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new BizException("扩展 JSON 不是合法 JSON");
        }
    }

    private DictTypeView toTypeView(SysDictType type) {
        if (type == null) {
            return null;
        }
        Long itemCount = dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, type.getTypeCode()));
        return new DictTypeView(
                type.getId(),
                type.getTypeCode(),
                type.getTypeName(),
                type.getDescription(),
                type.getStatus(),
                !Integer.valueOf(0).equals(type.getEditable()),
                safe(itemCount),
                type.getCreatedAt(),
                type.getUpdatedAt()
        );
    }

    private DictItemView toItemView(SysDictItem item) {
        if (item == null) {
            return null;
        }
        return new DictItemView(
                item.getId(),
                item.getTypeCode(),
                item.getItemLabel(),
                item.getItemValue(),
                item.getSortNo(),
                item.getCssClass(),
                item.getExtraJson(),
                item.getStatus(),
                !Integer.valueOf(0).equals(item.getEditable()),
                item.getRemark(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
