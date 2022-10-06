package com.example.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@TableName("t_publish")
@Data
public class Publish {
    private int pid;
    private String title;
    private String content;
    private String address;
    private int isimg;
    private Date pubtime;
    private int status;
    private String openId;


    @TableField(select = false)
    private List<Map<String,Object>> replyList; //回复
    @TableField(select = false)
    private List<Map<String,Object>> likeList;  //点赞
    @TableField(select = false)
    private List<PublishImg> imgList;       //图片
    @TableField(select = false)
    private WxUserInfo userInfo;       //用户

    
}
