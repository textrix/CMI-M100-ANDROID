package me.autolock.m100.cmi

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import me.autolock.m100.cmi.databinding.ActivityMainBinding
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModel<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(
            this,
            R.layout.activity_main
        )
        binding.viewModel = viewModel

        viewModel.logText.observe(this, {
            binding.logText.append(it)
            if ((binding.logText.measuredHeight - binding.logScroll.scrollY) <= (binding.logScroll.height + binding.logText.lineHeight)) {
                binding.logText.post {
                    binding.logScroll.smoothScrollTo(0, binding.logText.bottom)
                }
            }
        })
    }
}
