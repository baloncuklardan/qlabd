package com.abdullahql.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdullahql.app.databinding.ActivityPairingBinding
import com.abdullahql.app.remote.FirebaseLocationRepository
import kotlinx.coroutines.launch

class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val uid = FirebaseLocationRepository.ensureSignedIn()
            val code = FirebaseLocationRepository.generatePairingCode(uid)
            binding.myCodeText.text = code
        }

        binding.btnConfirmPair.setOnClickListener {
            val code = binding.enterCodeField.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(this, "6 haneli kodu gir", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val uid = FirebaseLocationRepository.ensureSignedIn()
                val ok = FirebaseLocationRepository.pairWithCode(uid, code)
                if (ok) {
                    Toast.makeText(this@PairingActivity, "Eşleştirme başarılı!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@PairingActivity, "Kod bulunamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
