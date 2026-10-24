/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.xpath.regex;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;

/**
 * The Unicode block names usable in <code>\p{Is...}</code> regular expression
 * escapes. XSD 1.1 section G.4.2.3 takes the block names and ranges from the
 * Unicode database of the supported version (Unicode 15.0, the version of the
 * JDK 21 character tables); the XSD 1.0 names are kept as aliases, the
 * superseded Unicode 3.1 names keep their old ranges and the surrogate blocks
 * contain no character at all. Generated from Blocks-15.0.0.txt and the XSD 1.0
 * block table of go-xslt.
 *
 * @author Philip Helger
 */
@Immutable
final class UnicodeBlocks
{
  private static final ICommonsMap <String, int []> UCD_BLOCKS;
  private static final ICommonsMap <String, int []> XSD_BLOCKS;
  /** Unicode 3.1 names that keep their old range (XSD 1.1 section G.4.2.3) */
  private static final ICommonsSet <String> LEGACY = new CommonsHashSet <> ("Greek", "CombiningMarksforSymbols", "PrivateUse");

  static
  {
    ICommonsMap <String, int []> aMap = new CommonsHashMap <> ();
    _put (aMap, "BasicLatin", 0x0000, 0x007F);
    _put (aMap, "Latin-1Supplement", 0x0080, 0x00FF);
    _put (aMap, "LatinExtended-A", 0x0100, 0x017F);
    _put (aMap, "LatinExtended-B", 0x0180, 0x024F);
    _put (aMap, "IPAExtensions", 0x0250, 0x02AF);
    _put (aMap, "SpacingModifierLetters", 0x02B0, 0x02FF);
    _put (aMap, "CombiningDiacriticalMarks", 0x0300, 0x036F);
    _put (aMap, "GreekandCoptic", 0x0370, 0x03FF);
    _put (aMap, "Cyrillic", 0x0400, 0x04FF);
    _put (aMap, "CyrillicSupplement", 0x0500, 0x052F);
    _put (aMap, "Armenian", 0x0530, 0x058F);
    _put (aMap, "Hebrew", 0x0590, 0x05FF);
    _put (aMap, "Arabic", 0x0600, 0x06FF);
    _put (aMap, "Syriac", 0x0700, 0x074F);
    _put (aMap, "ArabicSupplement", 0x0750, 0x077F);
    _put (aMap, "Thaana", 0x0780, 0x07BF);
    _put (aMap, "NKo", 0x07C0, 0x07FF);
    _put (aMap, "Samaritan", 0x0800, 0x083F);
    _put (aMap, "Mandaic", 0x0840, 0x085F);
    _put (aMap, "SyriacSupplement", 0x0860, 0x086F);
    _put (aMap, "ArabicExtended-B", 0x0870, 0x089F);
    _put (aMap, "ArabicExtended-A", 0x08A0, 0x08FF);
    _put (aMap, "Devanagari", 0x0900, 0x097F);
    _put (aMap, "Bengali", 0x0980, 0x09FF);
    _put (aMap, "Gurmukhi", 0x0A00, 0x0A7F);
    _put (aMap, "Gujarati", 0x0A80, 0x0AFF);
    _put (aMap, "Oriya", 0x0B00, 0x0B7F);
    _put (aMap, "Tamil", 0x0B80, 0x0BFF);
    _put (aMap, "Telugu", 0x0C00, 0x0C7F);
    _put (aMap, "Kannada", 0x0C80, 0x0CFF);
    _put (aMap, "Malayalam", 0x0D00, 0x0D7F);
    _put (aMap, "Sinhala", 0x0D80, 0x0DFF);
    _put (aMap, "Thai", 0x0E00, 0x0E7F);
    _put (aMap, "Lao", 0x0E80, 0x0EFF);
    _put (aMap, "Tibetan", 0x0F00, 0x0FFF);
    _put (aMap, "Myanmar", 0x1000, 0x109F);
    _put (aMap, "Georgian", 0x10A0, 0x10FF);
    _put (aMap, "HangulJamo", 0x1100, 0x11FF);
    _put (aMap, "Ethiopic", 0x1200, 0x137F);
    _put (aMap, "EthiopicSupplement", 0x1380, 0x139F);
    _put (aMap, "Cherokee", 0x13A0, 0x13FF);
    _put (aMap, "UnifiedCanadianAboriginalSyllabics", 0x1400, 0x167F);
    _put (aMap, "Ogham", 0x1680, 0x169F);
    _put (aMap, "Runic", 0x16A0, 0x16FF);
    _put (aMap, "Tagalog", 0x1700, 0x171F);
    _put (aMap, "Hanunoo", 0x1720, 0x173F);
    _put (aMap, "Buhid", 0x1740, 0x175F);
    _put (aMap, "Tagbanwa", 0x1760, 0x177F);
    _put (aMap, "Khmer", 0x1780, 0x17FF);
    _put (aMap, "Mongolian", 0x1800, 0x18AF);
    _put (aMap, "UnifiedCanadianAboriginalSyllabicsExtended", 0x18B0, 0x18FF);
    _put (aMap, "Limbu", 0x1900, 0x194F);
    _put (aMap, "TaiLe", 0x1950, 0x197F);
    _put (aMap, "NewTaiLue", 0x1980, 0x19DF);
    _put (aMap, "KhmerSymbols", 0x19E0, 0x19FF);
    _put (aMap, "Buginese", 0x1A00, 0x1A1F);
    _put (aMap, "TaiTham", 0x1A20, 0x1AAF);
    _put (aMap, "CombiningDiacriticalMarksExtended", 0x1AB0, 0x1AFF);
    _put (aMap, "Balinese", 0x1B00, 0x1B7F);
    _put (aMap, "Sundanese", 0x1B80, 0x1BBF);
    _put (aMap, "Batak", 0x1BC0, 0x1BFF);
    _put (aMap, "Lepcha", 0x1C00, 0x1C4F);
    _put (aMap, "OlChiki", 0x1C50, 0x1C7F);
    _put (aMap, "CyrillicExtended-C", 0x1C80, 0x1C8F);
    _put (aMap, "GeorgianExtended", 0x1C90, 0x1CBF);
    _put (aMap, "SundaneseSupplement", 0x1CC0, 0x1CCF);
    _put (aMap, "VedicExtensions", 0x1CD0, 0x1CFF);
    _put (aMap, "PhoneticExtensions", 0x1D00, 0x1D7F);
    _put (aMap, "PhoneticExtensionsSupplement", 0x1D80, 0x1DBF);
    _put (aMap, "CombiningDiacriticalMarksSupplement", 0x1DC0, 0x1DFF);
    _put (aMap, "LatinExtendedAdditional", 0x1E00, 0x1EFF);
    _put (aMap, "GreekExtended", 0x1F00, 0x1FFF);
    _put (aMap, "GeneralPunctuation", 0x2000, 0x206F);
    _put (aMap, "SuperscriptsandSubscripts", 0x2070, 0x209F);
    _put (aMap, "CurrencySymbols", 0x20A0, 0x20CF);
    _put (aMap, "CombiningDiacriticalMarksforSymbols", 0x20D0, 0x20FF);
    _put (aMap, "LetterlikeSymbols", 0x2100, 0x214F);
    _put (aMap, "NumberForms", 0x2150, 0x218F);
    _put (aMap, "Arrows", 0x2190, 0x21FF);
    _put (aMap, "MathematicalOperators", 0x2200, 0x22FF);
    _put (aMap, "MiscellaneousTechnical", 0x2300, 0x23FF);
    _put (aMap, "ControlPictures", 0x2400, 0x243F);
    _put (aMap, "OpticalCharacterRecognition", 0x2440, 0x245F);
    _put (aMap, "EnclosedAlphanumerics", 0x2460, 0x24FF);
    _put (aMap, "BoxDrawing", 0x2500, 0x257F);
    _put (aMap, "BlockElements", 0x2580, 0x259F);
    _put (aMap, "GeometricShapes", 0x25A0, 0x25FF);
    _put (aMap, "MiscellaneousSymbols", 0x2600, 0x26FF);
    _put (aMap, "Dingbats", 0x2700, 0x27BF);
    _put (aMap, "MiscellaneousMathematicalSymbols-A", 0x27C0, 0x27EF);
    _put (aMap, "SupplementalArrows-A", 0x27F0, 0x27FF);
    _put (aMap, "BraillePatterns", 0x2800, 0x28FF);
    _put (aMap, "SupplementalArrows-B", 0x2900, 0x297F);
    _put (aMap, "MiscellaneousMathematicalSymbols-B", 0x2980, 0x29FF);
    _put (aMap, "SupplementalMathematicalOperators", 0x2A00, 0x2AFF);
    _put (aMap, "MiscellaneousSymbolsandArrows", 0x2B00, 0x2BFF);
    _put (aMap, "Glagolitic", 0x2C00, 0x2C5F);
    _put (aMap, "LatinExtended-C", 0x2C60, 0x2C7F);
    _put (aMap, "Coptic", 0x2C80, 0x2CFF);
    _put (aMap, "GeorgianSupplement", 0x2D00, 0x2D2F);
    _put (aMap, "Tifinagh", 0x2D30, 0x2D7F);
    _put (aMap, "EthiopicExtended", 0x2D80, 0x2DDF);
    _put (aMap, "CyrillicExtended-A", 0x2DE0, 0x2DFF);
    _put (aMap, "SupplementalPunctuation", 0x2E00, 0x2E7F);
    _put (aMap, "CJKRadicalsSupplement", 0x2E80, 0x2EFF);
    _put (aMap, "KangxiRadicals", 0x2F00, 0x2FDF);
    _put (aMap, "IdeographicDescriptionCharacters", 0x2FF0, 0x2FFF);
    _put (aMap, "CJKSymbolsandPunctuation", 0x3000, 0x303F);
    _put (aMap, "Hiragana", 0x3040, 0x309F);
    _put (aMap, "Katakana", 0x30A0, 0x30FF);
    _put (aMap, "Bopomofo", 0x3100, 0x312F);
    _put (aMap, "HangulCompatibilityJamo", 0x3130, 0x318F);
    _put (aMap, "Kanbun", 0x3190, 0x319F);
    _put (aMap, "BopomofoExtended", 0x31A0, 0x31BF);
    _put (aMap, "CJKStrokes", 0x31C0, 0x31EF);
    _put (aMap, "KatakanaPhoneticExtensions", 0x31F0, 0x31FF);
    _put (aMap, "EnclosedCJKLettersandMonths", 0x3200, 0x32FF);
    _put (aMap, "CJKCompatibility", 0x3300, 0x33FF);
    _put (aMap, "CJKUnifiedIdeographsExtensionA", 0x3400, 0x4DBF);
    _put (aMap, "YijingHexagramSymbols", 0x4DC0, 0x4DFF);
    _put (aMap, "CJKUnifiedIdeographs", 0x4E00, 0x9FFF);
    _put (aMap, "YiSyllables", 0xA000, 0xA48F);
    _put (aMap, "YiRadicals", 0xA490, 0xA4CF);
    _put (aMap, "Lisu", 0xA4D0, 0xA4FF);
    _put (aMap, "Vai", 0xA500, 0xA63F);
    _put (aMap, "CyrillicExtended-B", 0xA640, 0xA69F);
    _put (aMap, "Bamum", 0xA6A0, 0xA6FF);
    _put (aMap, "ModifierToneLetters", 0xA700, 0xA71F);
    _put (aMap, "LatinExtended-D", 0xA720, 0xA7FF);
    _put (aMap, "SylotiNagri", 0xA800, 0xA82F);
    _put (aMap, "CommonIndicNumberForms", 0xA830, 0xA83F);
    _put (aMap, "Phags-pa", 0xA840, 0xA87F);
    _put (aMap, "Saurashtra", 0xA880, 0xA8DF);
    _put (aMap, "DevanagariExtended", 0xA8E0, 0xA8FF);
    _put (aMap, "KayahLi", 0xA900, 0xA92F);
    _put (aMap, "Rejang", 0xA930, 0xA95F);
    _put (aMap, "HangulJamoExtended-A", 0xA960, 0xA97F);
    _put (aMap, "Javanese", 0xA980, 0xA9DF);
    _put (aMap, "MyanmarExtended-B", 0xA9E0, 0xA9FF);
    _put (aMap, "Cham", 0xAA00, 0xAA5F);
    _put (aMap, "MyanmarExtended-A", 0xAA60, 0xAA7F);
    _put (aMap, "TaiViet", 0xAA80, 0xAADF);
    _put (aMap, "MeeteiMayekExtensions", 0xAAE0, 0xAAFF);
    _put (aMap, "EthiopicExtended-A", 0xAB00, 0xAB2F);
    _put (aMap, "LatinExtended-E", 0xAB30, 0xAB6F);
    _put (aMap, "CherokeeSupplement", 0xAB70, 0xABBF);
    _put (aMap, "MeeteiMayek", 0xABC0, 0xABFF);
    _put (aMap, "HangulSyllables", 0xAC00, 0xD7AF);
    _put (aMap, "HangulJamoExtended-B", 0xD7B0, 0xD7FF);
    _put (aMap, "HighSurrogates", 0xD800, 0xDB7F);
    _put (aMap, "HighPrivateUseSurrogates", 0xDB80, 0xDBFF);
    _put (aMap, "LowSurrogates", 0xDC00, 0xDFFF);
    _put (aMap, "PrivateUseArea", 0xE000, 0xF8FF);
    _put (aMap, "CJKCompatibilityIdeographs", 0xF900, 0xFAFF);
    _put (aMap, "AlphabeticPresentationForms", 0xFB00, 0xFB4F);
    _put (aMap, "ArabicPresentationForms-A", 0xFB50, 0xFDFF);
    _put (aMap, "VariationSelectors", 0xFE00, 0xFE0F);
    _put (aMap, "VerticalForms", 0xFE10, 0xFE1F);
    _put (aMap, "CombiningHalfMarks", 0xFE20, 0xFE2F);
    _put (aMap, "CJKCompatibilityForms", 0xFE30, 0xFE4F);
    _put (aMap, "SmallFormVariants", 0xFE50, 0xFE6F);
    _put (aMap, "ArabicPresentationForms-B", 0xFE70, 0xFEFF);
    _put (aMap, "HalfwidthandFullwidthForms", 0xFF00, 0xFFEF);
    _put (aMap, "Specials", 0xFFF0, 0xFFFF);
    _put (aMap, "LinearBSyllabary", 0x10000, 0x1007F);
    _put (aMap, "LinearBIdeograms", 0x10080, 0x100FF);
    _put (aMap, "AegeanNumbers", 0x10100, 0x1013F);
    _put (aMap, "AncientGreekNumbers", 0x10140, 0x1018F);
    _put (aMap, "AncientSymbols", 0x10190, 0x101CF);
    _put (aMap, "PhaistosDisc", 0x101D0, 0x101FF);
    _put (aMap, "Lycian", 0x10280, 0x1029F);
    _put (aMap, "Carian", 0x102A0, 0x102DF);
    _put (aMap, "CopticEpactNumbers", 0x102E0, 0x102FF);
    _put (aMap, "OldItalic", 0x10300, 0x1032F);
    _put (aMap, "Gothic", 0x10330, 0x1034F);
    _put (aMap, "OldPermic", 0x10350, 0x1037F);
    _put (aMap, "Ugaritic", 0x10380, 0x1039F);
    _put (aMap, "OldPersian", 0x103A0, 0x103DF);
    _put (aMap, "Deseret", 0x10400, 0x1044F);
    _put (aMap, "Shavian", 0x10450, 0x1047F);
    _put (aMap, "Osmanya", 0x10480, 0x104AF);
    _put (aMap, "Osage", 0x104B0, 0x104FF);
    _put (aMap, "Elbasan", 0x10500, 0x1052F);
    _put (aMap, "CaucasianAlbanian", 0x10530, 0x1056F);
    _put (aMap, "Vithkuqi", 0x10570, 0x105BF);
    _put (aMap, "LinearA", 0x10600, 0x1077F);
    _put (aMap, "LatinExtended-F", 0x10780, 0x107BF);
    _put (aMap, "CypriotSyllabary", 0x10800, 0x1083F);
    _put (aMap, "ImperialAramaic", 0x10840, 0x1085F);
    _put (aMap, "Palmyrene", 0x10860, 0x1087F);
    _put (aMap, "Nabataean", 0x10880, 0x108AF);
    _put (aMap, "Hatran", 0x108E0, 0x108FF);
    _put (aMap, "Phoenician", 0x10900, 0x1091F);
    _put (aMap, "Lydian", 0x10920, 0x1093F);
    _put (aMap, "MeroiticHieroglyphs", 0x10980, 0x1099F);
    _put (aMap, "MeroiticCursive", 0x109A0, 0x109FF);
    _put (aMap, "Kharoshthi", 0x10A00, 0x10A5F);
    _put (aMap, "OldSouthArabian", 0x10A60, 0x10A7F);
    _put (aMap, "OldNorthArabian", 0x10A80, 0x10A9F);
    _put (aMap, "Manichaean", 0x10AC0, 0x10AFF);
    _put (aMap, "Avestan", 0x10B00, 0x10B3F);
    _put (aMap, "InscriptionalParthian", 0x10B40, 0x10B5F);
    _put (aMap, "InscriptionalPahlavi", 0x10B60, 0x10B7F);
    _put (aMap, "PsalterPahlavi", 0x10B80, 0x10BAF);
    _put (aMap, "OldTurkic", 0x10C00, 0x10C4F);
    _put (aMap, "OldHungarian", 0x10C80, 0x10CFF);
    _put (aMap, "HanifiRohingya", 0x10D00, 0x10D3F);
    _put (aMap, "RumiNumeralSymbols", 0x10E60, 0x10E7F);
    _put (aMap, "Yezidi", 0x10E80, 0x10EBF);
    _put (aMap, "ArabicExtended-C", 0x10EC0, 0x10EFF);
    _put (aMap, "OldSogdian", 0x10F00, 0x10F2F);
    _put (aMap, "Sogdian", 0x10F30, 0x10F6F);
    _put (aMap, "OldUyghur", 0x10F70, 0x10FAF);
    _put (aMap, "Chorasmian", 0x10FB0, 0x10FDF);
    _put (aMap, "Elymaic", 0x10FE0, 0x10FFF);
    _put (aMap, "Brahmi", 0x11000, 0x1107F);
    _put (aMap, "Kaithi", 0x11080, 0x110CF);
    _put (aMap, "SoraSompeng", 0x110D0, 0x110FF);
    _put (aMap, "Chakma", 0x11100, 0x1114F);
    _put (aMap, "Mahajani", 0x11150, 0x1117F);
    _put (aMap, "Sharada", 0x11180, 0x111DF);
    _put (aMap, "SinhalaArchaicNumbers", 0x111E0, 0x111FF);
    _put (aMap, "Khojki", 0x11200, 0x1124F);
    _put (aMap, "Multani", 0x11280, 0x112AF);
    _put (aMap, "Khudawadi", 0x112B0, 0x112FF);
    _put (aMap, "Grantha", 0x11300, 0x1137F);
    _put (aMap, "Newa", 0x11400, 0x1147F);
    _put (aMap, "Tirhuta", 0x11480, 0x114DF);
    _put (aMap, "Siddham", 0x11580, 0x115FF);
    _put (aMap, "Modi", 0x11600, 0x1165F);
    _put (aMap, "MongolianSupplement", 0x11660, 0x1167F);
    _put (aMap, "Takri", 0x11680, 0x116CF);
    _put (aMap, "Ahom", 0x11700, 0x1174F);
    _put (aMap, "Dogra", 0x11800, 0x1184F);
    _put (aMap, "WarangCiti", 0x118A0, 0x118FF);
    _put (aMap, "DivesAkuru", 0x11900, 0x1195F);
    _put (aMap, "Nandinagari", 0x119A0, 0x119FF);
    _put (aMap, "ZanabazarSquare", 0x11A00, 0x11A4F);
    _put (aMap, "Soyombo", 0x11A50, 0x11AAF);
    _put (aMap, "UnifiedCanadianAboriginalSyllabicsExtended-A", 0x11AB0, 0x11ABF);
    _put (aMap, "PauCinHau", 0x11AC0, 0x11AFF);
    _put (aMap, "DevanagariExtended-A", 0x11B00, 0x11B5F);
    _put (aMap, "Bhaiksuki", 0x11C00, 0x11C6F);
    _put (aMap, "Marchen", 0x11C70, 0x11CBF);
    _put (aMap, "MasaramGondi", 0x11D00, 0x11D5F);
    _put (aMap, "GunjalaGondi", 0x11D60, 0x11DAF);
    _put (aMap, "Makasar", 0x11EE0, 0x11EFF);
    _put (aMap, "Kawi", 0x11F00, 0x11F5F);
    _put (aMap, "LisuSupplement", 0x11FB0, 0x11FBF);
    _put (aMap, "TamilSupplement", 0x11FC0, 0x11FFF);
    _put (aMap, "Cuneiform", 0x12000, 0x123FF);
    _put (aMap, "CuneiformNumbersandPunctuation", 0x12400, 0x1247F);
    _put (aMap, "EarlyDynasticCuneiform", 0x12480, 0x1254F);
    _put (aMap, "Cypro-Minoan", 0x12F90, 0x12FFF);
    _put (aMap, "EgyptianHieroglyphs", 0x13000, 0x1342F);
    _put (aMap, "EgyptianHieroglyphFormatControls", 0x13430, 0x1345F);
    _put (aMap, "AnatolianHieroglyphs", 0x14400, 0x1467F);
    _put (aMap, "BamumSupplement", 0x16800, 0x16A3F);
    _put (aMap, "Mro", 0x16A40, 0x16A6F);
    _put (aMap, "Tangsa", 0x16A70, 0x16ACF);
    _put (aMap, "BassaVah", 0x16AD0, 0x16AFF);
    _put (aMap, "PahawhHmong", 0x16B00, 0x16B8F);
    _put (aMap, "Medefaidrin", 0x16E40, 0x16E9F);
    _put (aMap, "Miao", 0x16F00, 0x16F9F);
    _put (aMap, "IdeographicSymbolsandPunctuation", 0x16FE0, 0x16FFF);
    _put (aMap, "Tangut", 0x17000, 0x187FF);
    _put (aMap, "TangutComponents", 0x18800, 0x18AFF);
    _put (aMap, "KhitanSmallScript", 0x18B00, 0x18CFF);
    _put (aMap, "TangutSupplement", 0x18D00, 0x18D7F);
    _put (aMap, "KanaExtended-B", 0x1AFF0, 0x1AFFF);
    _put (aMap, "KanaSupplement", 0x1B000, 0x1B0FF);
    _put (aMap, "KanaExtended-A", 0x1B100, 0x1B12F);
    _put (aMap, "SmallKanaExtension", 0x1B130, 0x1B16F);
    _put (aMap, "Nushu", 0x1B170, 0x1B2FF);
    _put (aMap, "Duployan", 0x1BC00, 0x1BC9F);
    _put (aMap, "ShorthandFormatControls", 0x1BCA0, 0x1BCAF);
    _put (aMap, "ZnamennyMusicalNotation", 0x1CF00, 0x1CFCF);
    _put (aMap, "ByzantineMusicalSymbols", 0x1D000, 0x1D0FF);
    _put (aMap, "MusicalSymbols", 0x1D100, 0x1D1FF);
    _put (aMap, "AncientGreekMusicalNotation", 0x1D200, 0x1D24F);
    _put (aMap, "KaktovikNumerals", 0x1D2C0, 0x1D2DF);
    _put (aMap, "MayanNumerals", 0x1D2E0, 0x1D2FF);
    _put (aMap, "TaiXuanJingSymbols", 0x1D300, 0x1D35F);
    _put (aMap, "CountingRodNumerals", 0x1D360, 0x1D37F);
    _put (aMap, "MathematicalAlphanumericSymbols", 0x1D400, 0x1D7FF);
    _put (aMap, "SuttonSignWriting", 0x1D800, 0x1DAAF);
    _put (aMap, "LatinExtended-G", 0x1DF00, 0x1DFFF);
    _put (aMap, "GlagoliticSupplement", 0x1E000, 0x1E02F);
    _put (aMap, "CyrillicExtended-D", 0x1E030, 0x1E08F);
    _put (aMap, "NyiakengPuachueHmong", 0x1E100, 0x1E14F);
    _put (aMap, "Toto", 0x1E290, 0x1E2BF);
    _put (aMap, "Wancho", 0x1E2C0, 0x1E2FF);
    _put (aMap, "NagMundari", 0x1E4D0, 0x1E4FF);
    _put (aMap, "EthiopicExtended-B", 0x1E7E0, 0x1E7FF);
    _put (aMap, "MendeKikakui", 0x1E800, 0x1E8DF);
    _put (aMap, "Adlam", 0x1E900, 0x1E95F);
    _put (aMap, "IndicSiyaqNumbers", 0x1EC70, 0x1ECBF);
    _put (aMap, "OttomanSiyaqNumbers", 0x1ED00, 0x1ED4F);
    _put (aMap, "ArabicMathematicalAlphabeticSymbols", 0x1EE00, 0x1EEFF);
    _put (aMap, "MahjongTiles", 0x1F000, 0x1F02F);
    _put (aMap, "DominoTiles", 0x1F030, 0x1F09F);
    _put (aMap, "PlayingCards", 0x1F0A0, 0x1F0FF);
    _put (aMap, "EnclosedAlphanumericSupplement", 0x1F100, 0x1F1FF);
    _put (aMap, "EnclosedIdeographicSupplement", 0x1F200, 0x1F2FF);
    _put (aMap, "MiscellaneousSymbolsandPictographs", 0x1F300, 0x1F5FF);
    _put (aMap, "Emoticons", 0x1F600, 0x1F64F);
    _put (aMap, "OrnamentalDingbats", 0x1F650, 0x1F67F);
    _put (aMap, "TransportandMapSymbols", 0x1F680, 0x1F6FF);
    _put (aMap, "AlchemicalSymbols", 0x1F700, 0x1F77F);
    _put (aMap, "GeometricShapesExtended", 0x1F780, 0x1F7FF);
    _put (aMap, "SupplementalArrows-C", 0x1F800, 0x1F8FF);
    _put (aMap, "SupplementalSymbolsandPictographs", 0x1F900, 0x1F9FF);
    _put (aMap, "ChessSymbols", 0x1FA00, 0x1FA6F);
    _put (aMap, "SymbolsandPictographsExtended-A", 0x1FA70, 0x1FAFF);
    _put (aMap, "SymbolsforLegacyComputing", 0x1FB00, 0x1FBFF);
    _put (aMap, "CJKUnifiedIdeographsExtensionB", 0x20000, 0x2A6DF);
    _put (aMap, "CJKUnifiedIdeographsExtensionC", 0x2A700, 0x2B73F);
    _put (aMap, "CJKUnifiedIdeographsExtensionD", 0x2B740, 0x2B81F);
    _put (aMap, "CJKUnifiedIdeographsExtensionE", 0x2B820, 0x2CEAF);
    _put (aMap, "CJKUnifiedIdeographsExtensionF", 0x2CEB0, 0x2EBEF);
    _put (aMap, "CJKCompatibilityIdeographsSupplement", 0x2F800, 0x2FA1F);
    _put (aMap, "CJKUnifiedIdeographsExtensionG", 0x30000, 0x3134F);
    _put (aMap, "CJKUnifiedIdeographsExtensionH", 0x31350, 0x323AF);
    _put (aMap, "Tags", 0xE0000, 0xE007F);
    _put (aMap, "VariationSelectorsSupplement", 0xE0100, 0xE01EF);
    _put (aMap, "SupplementaryPrivateUseArea-A", 0xF0000, 0xFFFFF);
    _put (aMap, "SupplementaryPrivateUseArea-B", 0x100000, 0x10FFFF);
    UCD_BLOCKS = aMap;
    aMap = new CommonsHashMap <> ();
    _put (aMap, "BasicLatin", 0x0000, 0x007F);
    _put (aMap, "Latin-1Supplement", 0x0080, 0x00FF);
    _put (aMap, "LatinExtended-A", 0x0100, 0x017F);
    _put (aMap, "LatinExtended-B", 0x0180, 0x024F);
    _put (aMap, "IPAExtensions", 0x0250, 0x02AF);
    _put (aMap, "SpacingModifierLetters", 0x02B0, 0x02FF);
    _put (aMap, "CombiningDiacriticalMarks", 0x0300, 0x036F);
    _put (aMap, "GreekandCoptic", 0x0370, 0x03FF);
    _put (aMap, "Greek", 0x0370, 0x03FF);
    _put (aMap, "Cyrillic", 0x0400, 0x04FF);
    _put (aMap, "Armenian", 0x0530, 0x058F);
    _put (aMap, "Hebrew", 0x0590, 0x05FF);
    _put (aMap, "Arabic", 0x0600, 0x06FF);
    _put (aMap, "Syriac", 0x0700, 0x074F);
    _put (aMap, "Thaana", 0x0780, 0x07BF);
    _put (aMap, "Devanagari", 0x0900, 0x097F);
    _put (aMap, "Bengali", 0x0980, 0x09FF);
    _put (aMap, "Gurmukhi", 0x0A00, 0x0A7F);
    _put (aMap, "Gujarati", 0x0A80, 0x0AFF);
    _put (aMap, "Oriya", 0x0B00, 0x0B7F);
    _put (aMap, "Tamil", 0x0B80, 0x0BFF);
    _put (aMap, "Telugu", 0x0C00, 0x0C7F);
    _put (aMap, "Kannada", 0x0C80, 0x0CFF);
    _put (aMap, "Malayalam", 0x0D00, 0x0D7F);
    _put (aMap, "Sinhala", 0x0D80, 0x0DFF);
    _put (aMap, "Thai", 0x0E00, 0x0E7F);
    _put (aMap, "Lao", 0x0E80, 0x0EFF);
    _put (aMap, "Tibetan", 0x0F00, 0x0FFF);
    _put (aMap, "Myanmar", 0x1000, 0x109F);
    _put (aMap, "Georgian", 0x10A0, 0x10FF);
    _put (aMap, "HangulJamo", 0x1100, 0x11FF);
    _put (aMap, "Ethiopic", 0x1200, 0x137F);
    _put (aMap, "Cherokee", 0x13A0, 0x13FF);
    _put (aMap, "UnifiedCanadianAboriginalSyllabics", 0x1400, 0x167F);
    _put (aMap, "Ogham", 0x1680, 0x169F);
    _put (aMap, "Runic", 0x16A0, 0x16FF);
    _put (aMap, "Khmer", 0x1780, 0x17FF);
    _put (aMap, "Mongolian", 0x1800, 0x18AF);
    _put (aMap, "LatinExtendedAdditional", 0x1E00, 0x1EFF);
    _put (aMap, "GreekExtended", 0x1F00, 0x1FFF);
    _put (aMap, "GeneralPunctuation", 0x2000, 0x206F);
    _put (aMap, "SuperscriptsandSubscripts", 0x2070, 0x209F);
    _put (aMap, "CurrencySymbols", 0x20A0, 0x20CF);
    _put (aMap, "CombiningDiacriticalMarksforSymbols", 0x20D0, 0x20FF);
    _put (aMap, "CombiningMarksforSymbols", 0x20D0, 0x20FF);
    _put (aMap, "LetterlikeSymbols", 0x2100, 0x214F);
    _put (aMap, "NumberForms", 0x2150, 0x218F);
    _put (aMap, "Arrows", 0x2190, 0x21FF);
    _put (aMap, "MathematicalOperators", 0x2200, 0x22FF);
    _put (aMap, "MiscellaneousTechnical", 0x2300, 0x23FF);
    _put (aMap, "ControlPictures", 0x2400, 0x243F);
    _put (aMap, "OpticalCharacterRecognition", 0x2440, 0x245F);
    _put (aMap, "EnclosedAlphanumerics", 0x2460, 0x24FF);
    _put (aMap, "BoxDrawing", 0x2500, 0x257F);
    _put (aMap, "BlockElements", 0x2580, 0x259F);
    _put (aMap, "GeometricShapes", 0x25A0, 0x25FF);
    _put (aMap, "MiscellaneousSymbols", 0x2600, 0x26FF);
    _put (aMap, "Dingbats", 0x2700, 0x27BF);
    _put (aMap, "BraillePatterns", 0x2800, 0x28FF);
    _put (aMap, "CJKRadicalsSupplement", 0x2E80, 0x2EFF);
    _put (aMap, "KangxiRadicals", 0x2F00, 0x2FDF);
    _put (aMap, "IdeographicDescriptionCharacters", 0x2FF0, 0x2FFF);
    _put (aMap, "CJKSymbolsandPunctuation", 0x3000, 0x303F);
    _put (aMap, "Hiragana", 0x3040, 0x309F);
    _put (aMap, "Katakana", 0x30A0, 0x30FF);
    _put (aMap, "Bopomofo", 0x3100, 0x312F);
    _put (aMap, "HangulCompatibilityJamo", 0x3130, 0x318F);
    _put (aMap, "Kanbun", 0x3190, 0x319F);
    _put (aMap, "BopomofoExtended", 0x31A0, 0x31BF);
    _put (aMap, "EnclosedCJKLettersandMonths", 0x3200, 0x32FF);
    _put (aMap, "CJKCompatibility", 0x3300, 0x33FF);
    _put (aMap, "CJKUnifiedIdeographsExtensionA", 0x3400, 0x4DBF);
    _put (aMap, "CJKUnifiedIdeographs", 0x4E00, 0x9FFF);
    _put (aMap, "YiSyllables", 0xA000, 0xA48F);
    _put (aMap, "YiRadicals", 0xA490, 0xA4CF);
    _put (aMap, "HangulSyllables", 0xAC00, 0xD7AF);
    _put (aMap, "HighSurrogates", -1, -1);
    _put (aMap, "HighPrivateUseSurrogates", -1, -1);
    _put (aMap, "LowSurrogates", -1, -1);
    _put (aMap, "PrivateUseArea", 0xE000, 0xF8FF);
    _put (aMap, "PrivateUse", 0xE000, 0xF8FF);
    _put (aMap, "CJKCompatibilityIdeographs", 0xF900, 0xFAFF);
    _put (aMap, "AlphabeticPresentationForms", 0xFB00, 0xFB4F);
    _put (aMap, "ArabicPresentationForms-A", 0xFB50, 0xFDFF);
    _put (aMap, "CombiningHalfMarks", 0xFE20, 0xFE2F);
    _put (aMap, "CJKCompatibilityForms", 0xFE30, 0xFE4F);
    _put (aMap, "SmallFormVariants", 0xFE50, 0xFE6F);
    _put (aMap, "ArabicPresentationForms-B", 0xFE70, 0xFEFF);
    _put (aMap, "HalfwidthandFullwidthForms", 0xFF00, 0xFFEF);
    _put (aMap, "Specials", 0xFFF0, 0xFFFD);
    _put (aMap, "OldItalic", 0x10300, 0x1032F);
    _put (aMap, "Gothic", 0x10330, 0x1034F);
    _put (aMap, "Deseret", 0x10400, 0x1044F);
    _put (aMap, "ByzantineMusicalSymbols", 0x1D000, 0x1D0FF);
    _put (aMap, "MusicalSymbols", 0x1D100, 0x1D1FF);
    _put (aMap, "MathematicalAlphanumericSymbols", 0x1D400, 0x1D7FF);
    _put (aMap, "Emoticons", 0x1F600, 0x1F64F);
    _put (aMap, "CJKUnifiedIdeographsExtensionB", 0x20000, 0x2A6DF);
    _put (aMap, "CJKCompatibilityIdeographsSupplement", 0x2F800, 0x2FA1F);
    _put (aMap, "Tags", 0xE0000, 0xE007F);
    _put (aMap, "SupplementaryPrivateUseArea-A", 0xF0000, 0xFFFFD);
    _put (aMap, "SupplementaryPrivateUseArea-B", 0x100000, 0x10FFFD);
    XSD_BLOCKS = aMap;
  }

  @PresentForCodeCoverage
  private static final UnicodeBlocks INSTANCE = new UnicodeBlocks ();

  private UnicodeBlocks ()
  {}

  private static void _put (@NonNull final ICommonsMap <String, int []> aMap,
                            @NonNull final String sName,
                            final int nLow,
                            final int nHigh)
  {
    aMap.put (sName, new int [] { nLow, nHigh });
  }

  @NonNull
  private static int [] _withMore (@NonNull final String sName, @NonNull final int [] aRange)
  {
    // PrivateUse covers the BMP and both supplementary private use areas
    if ("PrivateUse".equals (sName))
      return new int [] { aRange[0], aRange[1], 0xF0000, 0xFFFFD, 0x100000, 0x10FFFD };
    return aRange.clone ();
  }

  /**
   * @param sName
   *        Block name without the "Is" prefix.
   * @return The ranges of the block as pairs (low, high) in one array, an empty
   *         array for a surrogate block, or <code>null</code> for an unknown
   *         block.
   */
  @Nullable
  static int [] getBlockRanges (@NonNull final String sName)
  {
    final int [] aXsd = XSD_BLOCKS.get (sName);
    if (aXsd != null && (aXsd[0] < 0 || LEGACY.contains (sName)))
      return aXsd[0] < 0 ? new int [0] : _withMore (sName, aXsd);
    final int [] aUcd = UCD_BLOCKS.get (sName);
    if (aUcd != null)
      return aUcd.clone ();
    return aXsd == null ? null : _withMore (sName, aXsd);
  }
}
