package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonMagenta
import com.example.viewmodel.QuickPrompt

@Composable
fun QuickPromptChips(
    prompts: List<QuickPrompt>,
    onPromptSelected: (QuickPrompt) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEachIndexed { index, prompt ->
            val isAction = prompt.actionUrl != null
            val borderColor = if (isAction) ElectricCyan.copy(alpha = 0.5f) else NeonMagenta.copy(alpha = 0.4f)
            val textColor = if (isAction) ElectricCyan else Color.White

            OutlinedButton(
                onClick = { onPromptSelected(prompt) },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, borderColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CyberSurfaceVariant.copy(alpha = 0.8f)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("quick_prompt_chip_$index")
            ) {
                Text(
                    text = prompt.label,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
