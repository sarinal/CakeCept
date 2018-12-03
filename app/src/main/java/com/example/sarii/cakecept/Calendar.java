package com.example.sarii.cakecept;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.CalendarView.OnDateChangeListener;
import android.widget.Toast;
import android.app.Activity;


public class Calendar extends AppCompatActivity {

    private Toolbar mToolbar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        mToolbar = (Toolbar) findViewById(R.id.action_bar);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setHomeButtonEnabled(true);

    }


}
