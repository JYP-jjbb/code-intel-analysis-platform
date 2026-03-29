package com.mickey.onlineordering.onlineorderingserver.service;

import com.mickey.onlineordering.onlineorderingserver.common.PageResult;
import com.mickey.onlineordering.onlineorderingserver.dto.DishQueryDto;
import com.mickey.onlineordering.onlineorderingserver.dto.DishSaveDto;
import com.mickey.onlineordering.onlineorderingserver.vo.DishDetailVo;
import com.mickey.onlineordering.onlineorderingserver.vo.DishListVo;

/**
 * 菜品服务接口
 */
public interface DishService {

    PageResult<DishListVo> getDishPage(DishQueryDto queryDto);
    DishDetailVo getDishById(Long id);
    void addDish(DishSaveDto dto);
    void updateDish(DishSaveDto dto);
    void deleteDish(Long id);
}











