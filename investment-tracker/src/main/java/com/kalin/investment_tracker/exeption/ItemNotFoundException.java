package com.kalin.investment_tracker.exeption;

public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(Long id){
        super("Could not find user with id "+ id);
    }
}
