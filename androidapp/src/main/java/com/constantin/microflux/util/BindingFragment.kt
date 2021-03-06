package com.constantin.microflux.util

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import dagger.android.support.DaggerFragment

abstract class BindingFragment<B : ViewBinding>(
    private val viewInflater: (LayoutInflater, ViewGroup?, Boolean) -> B
) : DaggerFragment() {

    protected val supportActivity: AppCompatActivity?
        get() = activity as? AppCompatActivity

    private var binding: B? = null

    protected abstract fun onBindingCreated(binding: B, savedInstanceState: Bundle?)


    fun requireBinding(): B {
        return checkNotNull(binding)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return viewInflater(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireBinding()
        // binding.root.setEdgeToEdgeSystemUiFlags(true)
        onBindingCreated(binding, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
