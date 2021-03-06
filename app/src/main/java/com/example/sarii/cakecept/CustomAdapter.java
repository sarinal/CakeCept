package com.example.sarii.cakecept;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


public class CustomAdapter extends PagerAdapter {

    private int[] images = {R.drawable.cakedesign1, R.drawable.cakedesign2, R.drawable.cakedesign3};
    private LayoutInflater inflater;
    private Context ctx;

    public CustomAdapter(Context ctx){
        this.ctx = ctx;
    }
    @Override
    public int getCount() {
        return images.length;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.swipe,container,false);
        ImageView img = (ImageView)v.findViewById(R.id.image_view);
        TextView tv =(TextView)v.findViewById(R.id.picture_name);
        img.setImageResource(images[position]);
        tv.setText("Image:" +position);
        container.addView(v);
        return  v;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.invalidate();
    }

    @Override
    public boolean isViewFromObject(View view, Object o) {
        return false;
    }


}
