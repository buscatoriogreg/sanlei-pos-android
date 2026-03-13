package com.sanlei.pos.ui.pos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.sanlei.pos.R;
import com.sanlei.pos.data.db.entity.ProductEntity;
import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(ProductEntity product);
    }

    private List<ProductEntity> products = new ArrayList<>();
    private OnProductClickListener listener;

    public ProductAdapter(OnProductClickListener listener) {
        this.listener = listener;
    }

    public void setProducts(List<ProductEntity> products) {
        this.products = products;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProductEntity product = products.get(position);
        holder.txtProductName.setText(product.name);
        holder.txtGenericName.setText(product.genericName != null ? product.genericName : "");
        holder.txtGenericName.setVisibility(product.genericName != null && !product.genericName.isEmpty() ? View.VISIBLE : View.GONE);
        holder.txtPrice.setText(String.format("P%,.2f", product.sellingPrice));
        holder.txtStock.setText("Stock: " + product.stock);

        // Load image with Glide
        if (product.image != null && !product.image.isEmpty()) {
            String imageUrl = "https://sp.rgbpos.com/storage/" + product.image;
            Glide.with(holder.itemView.getContext())
                .load(imageUrl)
                .placeholder(R.drawable.circle_green)
                .into(holder.imgProduct);
            holder.imgProduct.setVisibility(View.VISIBLE);
        } else {
            holder.imgProduct.setVisibility(View.GONE);
        }

        // Stock warning
        if (product.stock <= 5) {
            holder.txtStock.setTextColor(0xFFDC2626); // red
        } else {
            holder.txtStock.setTextColor(0xFF6B7280); // gray
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProductClick(product);
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView txtProductName, txtGenericName, txtPrice, txtStock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            txtProductName = itemView.findViewById(R.id.txtProductName);
            txtGenericName = itemView.findViewById(R.id.txtGenericName);
            txtPrice = itemView.findViewById(R.id.txtPrice);
            txtStock = itemView.findViewById(R.id.txtStock);
        }
    }
}
