package com.sanlei.pos.ui.pos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.sanlei.pos.R;
import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    public interface CartActionListener {
        void onQuantityChanged(int position, int newQuantity);
        void onRemoveItem(int position);
    }

    private List<CartItem> items = new ArrayList<>();
    private CartActionListener listener;

    public CartAdapter(CartActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public List<CartItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem item = items.get(position);
        holder.txtItemName.setText(item.name);
        holder.txtUnitPrice.setText(String.format("P%,.2f", item.unitPrice));
        holder.txtQuantity.setText(String.valueOf(item.quantity));
        holder.txtLineTotal.setText(String.format("P%,.2f", item.unitPrice * item.quantity));

        // Load product image
        if (item.image != null && !item.image.isEmpty()) {
            String imageUrl = "https://sp.rgbpos.com/storage/" + item.image;
            Glide.with(holder.itemView.getContext())
                .load(imageUrl)
                .centerCrop()
                .placeholder(android.R.color.transparent)
                .into(holder.imgProduct);
        } else {
            holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
            holder.imgProduct.setAlpha(0.3f);
        }

        holder.btnPlus.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onQuantityChanged(pos, items.get(pos).quantity + 1);
            }
        });

        holder.btnMinus.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                int newQty = items.get(pos).quantity - 1;
                if (newQty > 0) {
                    listener.onQuantityChanged(pos, newQty);
                } else {
                    listener.onRemoveItem(pos);
                }
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onRemoveItem(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView txtItemName, txtUnitPrice, txtQuantity, txtLineTotal;
        View btnMinus, btnPlus;
        View btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgCartProduct);
            txtItemName = itemView.findViewById(R.id.txtCartProductName);
            txtUnitPrice = itemView.findViewById(R.id.txtCartUnitPrice);
            txtQuantity = itemView.findViewById(R.id.txtQuantity);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            txtLineTotal = itemView.findViewById(R.id.txtLineTotal);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
