'use server';

/**
 * @fileOverview A content category suggestion AI agent.
 *
 * - suggestContentCategories - A function that handles the content category suggestion process.
 * - SuggestContentCategoriesInput - The input type for the suggestContentCategories function.
 * - SuggestContentCategoriesOutput - The return type for the suggestContentCategories function.
 */

import {ai} from '@/ai/genkit';
import {z} from 'genkit';

const SuggestContentCategoriesInputSchema = z.object({
  content: z.string().describe('The unverified content to categorize.'),
});
export type SuggestContentCategoriesInput = z.infer<
  typeof SuggestContentCategoriesInputSchema
>;

const SuggestContentCategoriesOutputSchema = z.object({
  categories: z
    .array(z.string())
    .describe('Suggested categories for the content.'),
});
export type SuggestContentCategoriesOutput = z.infer<
  typeof SuggestContentCategoriesOutputSchema
>;

export async function suggestContentCategories(
  input: SuggestContentCategoriesInput
): Promise<SuggestContentCategoriesOutput> {
  return suggestContentCategoriesFlow(input);
}

const prompt = ai.definePrompt({
  name: 'suggestContentCategoriesPrompt',
  input: {schema: SuggestContentCategoriesInputSchema},
  output: {schema: SuggestContentCategoriesOutputSchema},
  prompt: `You are a content moderation expert. Given the following content, suggest a list of categories that would be appropriate for it.

Content: {{{content}}}

Categories:`, // Ensure that the output is suitable for JSON parsing
});

const suggestContentCategoriesFlow = ai.defineFlow(
  {
    name: 'suggestContentCategoriesFlow',
    inputSchema: SuggestContentCategoriesInputSchema,
    outputSchema: SuggestContentCategoriesOutputSchema,
  },
  async input => {
    const {output} = await prompt(input);
    return output!;
  }
);
