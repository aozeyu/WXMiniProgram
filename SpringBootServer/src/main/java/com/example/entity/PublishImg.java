package com.example.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("t_publish_image")
@Data
public class PublishImg {
    private int id;
    private int sort;
    private String src;
    private int pid;

}
