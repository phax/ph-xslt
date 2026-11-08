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
package com.helger.xsd;

/**
 * The result of matching a child-element sequence against a content model.
 *
 * @param matched
 *        <code>true</code> if the children satisfy the content model.
 * @param decided
 *        <code>false</code> if the matcher could not decide (budget exhausted,
 *        pathological model): the verdict is then undetermined, not "no
 *        match".
 * @author Philip Helger
 */
record XsdMatchResult (boolean matched, boolean decided)
{}
