package com.pando.app.features.home.ui.profile

import android.app.DatePickerDialog
import android.widget.ArrayAdapter
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentProfileBinding
import java.util.Calendar

class ProfileFragment : BaseFragment<FragmentProfileBinding>(FragmentProfileBinding::inflate) {
    private lateinit var datePickerDialog: DatePickerDialog

    override fun initData() {
    }

    override fun initView() {
        val genders = resources.getStringArray(R.array.gioi_tinh)
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_gender_item, genders)
        binding.genderTV.setAdapter(arrayAdapter)

        initDatePicker()
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    fun initDatePicker() {
        val dataSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            val formattedDay = "%02d".format(day)
            val formattedMonth = "%02d".format(month+1)

            val date: String = makeDateString(formattedDay, formattedMonth, year)

            binding.birthDateTV.setText(date)
        }

        binding.birthDateTV.setOnClickListener {
            val calendar = Calendar.getInstance()

            datePickerDialog = DatePickerDialog(
                requireContext(),
                dataSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.show()
        }
    }

    private fun makeDateString(day: String, month: String, year: Int): String {
        return "$day/$month/$year"
    }


}
