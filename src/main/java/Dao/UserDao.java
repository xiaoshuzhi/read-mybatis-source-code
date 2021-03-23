package Dao;

import entity.User;

public interface UserDao {
    User selectUser(Integer id);
}
